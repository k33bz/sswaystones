#!/usr/bin/env python3
"""Boot a real Fabric server with the freshly built sswaystones jar and run scripted checks.

The unit tests cover pure logic; this answers "does the jar actually load and work on a real
server of this Minecraft version". It needs no bots and no client: every check goes in through the
server console and reads the server's own answer. Standard library only, so it runs on a stock
GitHub runner. (Adapted from k33bz/sanctuary's scripts/server_test.py.)

What it covers:
  boot      the server reaches "Done", sswaystones logs its init line and injects its village
            pieces, and no mixin fails to apply (mixins apply lazily; the scenarios load them)
  commands  /sswaystones answers with this jar's version; /sswaystones list reads the storage
  templates every village waystone template (desert, plains, savanna, snowy, taiga) loads and
            places: a template naming a block or property this Minecraft version dropped fails here
  block     a waystone block placed by command exists and its block entity ticks (hologram,
            particles, storage lookup) without errors
  village   a whole plains village generates through the injected pools (JigsawPlacerMixin)

Which Minecraft: the newest stable release of this line (minecraft_version 26.1 tests 26.1.2,
the patch servers actually run). Writes build/server-test/versions.json for the README badges
and a Markdown summary to $GITHUB_STEP_SUMMARY. Exit code 0 = every check passed.

Usage: python3 scripts/server_test.py [--jar build/libs/x.jar] [--workdir build/server-test]
"""
import argparse
import glob
import json
import os
import queue
import re
import shutil
import subprocess
import sys
import threading
import time
import urllib.parse
import urllib.request
import zipfile

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
UA = {"User-Agent": "k33bz/sswaystones server-test (github actions)"}

# Log lines that mean a mixin (or the mod) failed.
FATAL = re.compile(
    r"Mixin apply failed|InvalidInjectionException|InvalidMixinException|MixinApplyError"
    r"|Critical injection failure|Exception ticking world|Encountered an unexpected exception"
    r"|Could not execute entrypoint|Error executing task|Failed to load structure"
    r"|Missing element|Unbound values in registry|Failed to load registries|Error generating chunk")

VILLAGE_BIOMES = ("desert", "plains", "savanna", "snowy", "taiga")


# ---------------------------------------------------------------- downloads

def http_json(url):
    with urllib.request.urlopen(urllib.request.Request(url, headers=UA), timeout=60) as r:
        return json.load(r)


def download(url, dest):
    with urllib.request.urlopen(urllib.request.Request(url, headers=UA), timeout=300) as r, \
            open(dest, "wb") as f:
        shutil.copyfileobj(r, f)


def props():
    out = {}
    with open(os.path.join(ROOT, "gradle.properties")) as f:
        for line in f:
            line = line.strip()
            if line and not line.startswith("#") and "=" in line:
                k, v = line.split("=", 1)
                out[k.strip()] = v.strip()
    return out


def modrinth_file(project, mc, want_version=None):
    """Primary file of a Modrinth project's Fabric build for `mc` (exact version if given)."""
    q = urllib.parse.urlencode({"game_versions": json.dumps([mc]), "loaders": json.dumps(["fabric"])})
    versions = http_json(f"https://api.modrinth.com/v2/project/{project}/version?{q}")
    if not versions:
        return None
    pick = next((v for v in versions if v["version_number"] == want_version), None) if want_version else None
    pick = pick or versions[0]
    f = next((x for x in pick["files"] if x.get("primary")), pick["files"][0])
    return pick["version_number"], f["url"], f["filename"]


# ---------------------------------------------------------------- server process

class Server:
    def __init__(self, workdir):
        self.workdir = workdir
        self.lines = queue.Queue()
        self.log = []
        self.fatal = []
        self.proc = None

    def start(self):
        self.proc = subprocess.Popen(
            ["java", "-Xms1G", "-Xmx2G", "-jar", "fabric-server-launch.jar", "nogui"],
            cwd=self.workdir, stdin=subprocess.PIPE, stdout=subprocess.PIPE,
            stderr=subprocess.STDOUT, text=True, bufsize=1)
        threading.Thread(target=self._pump, daemon=True).start()

    def _pump(self):
        for line in self.proc.stdout:
            line = line.rstrip("\n")
            self.log.append(line)
            if FATAL.search(line):
                self.fatal.append(line)
            self.lines.put(line)
        self.lines.put(None)  # process ended

    def send(self, cmd):
        self.proc.stdin.write(cmd + "\n")
        self.proc.stdin.flush()

    def wait_for(self, pattern, timeout):
        """Next line matching `pattern` (regex) within `timeout` s, else None."""
        rx = re.compile(pattern)
        end = time.time() + timeout
        while time.time() < end:
            try:
                line = self.lines.get(timeout=max(0.05, end - time.time()))
            except queue.Empty:
                break
            if line is None:
                return None
            if rx.search(line):
                return line
        return None

    def drain(self):
        while True:
            try:
                self.lines.get_nowait()
            except queue.Empty:
                return

    def test(self, cmd):
        """Run an `execute if ...` command; True = passed, False = failed, None = no answer."""
        self.drain()
        self.send(cmd)
        line = self.wait_for(r"Test (passed|failed)", 10)
        if line is None:
            return None
        return "Test passed" in line

    def poll(self, cmd, want, timeout):
        """Repeat `cmd` until it answers `want` or `timeout` s pass. Returns the last answer."""
        end = time.time() + timeout
        got = None
        while time.time() < end:
            got = self.test(cmd)
            if got == want:
                return got
            time.sleep(1)
        return got

    def alive(self):
        self.drain()
        self.send("list")
        return self.wait_for(r"There are \d+ of a max", 15) is not None

    def stop(self):
        if self.proc and self.proc.poll() is None:
            try:
                self.send("stop")
                self.proc.wait(timeout=60)
            except Exception:
                self.proc.kill()


# ---------------------------------------------------------------- setup

# What the server test actually booted with, for scripts/publish_badges.py (and humans).
TESTED = {}


def test_minecraft(line):
    """Newest stable release of this Minecraft line: '26.1' -> '26.1.2' when that exists."""
    stable = [v["version"] for v in http_json("https://meta.fabricmc.net/v2/versions/game") if v["stable"]]
    same = [v for v in stable if v == line or v.startswith(line + ".")]
    return same[0] if same else line   # meta lists newest first


def setup(workdir, jar, p):
    mc = test_minecraft(p["minecraft_version"])
    TESTED["minecraft_tested"] = mc
    os.makedirs(os.path.join(workdir, "mods"), exist_ok=True)
    notes = []

    installer = next(i["version"] for i in http_json("https://meta.fabricmc.net/v2/versions/installer") if i["stable"])
    download(f"https://meta.fabricmc.net/v2/versions/loader/{mc}/{p['loader_version']}/{installer}/server/jar",
             os.path.join(workdir, "fabric-server-launch.jar"))
    notes.append(f"Minecraft {mc} (newest release of the {p['minecraft_version']} line), "
                 f"Fabric loader {p['loader_version']} (installer {installer})")

    # The newest fabric-api for this Minecraft version, as a real server would run, not the pin.
    api = modrinth_file("fabric-api", mc)
    if api is None:
        raise SystemExit(f"no fabric-api build on Modrinth for {mc}")
    download(api[1], os.path.join(workdir, "mods", api[2]))
    notes.append(f"fabric-api {api[0]} (newest for {mc}; compiled against {p.get('fabric_api_version')})")
    TESTED["fabric_api_tested"] = api[0]

    # Polymer, sgui, server-translations and permissions-api are bundled in the jar (include).
    shutil.copy(jar, os.path.join(workdir, "mods", os.path.basename(jar)))
    notes.append(f"under test: {os.path.basename(jar)} (Polymer {p.get('polymer_version')} bundled)")

    with open(os.path.join(workdir, "eula.txt"), "w") as f:
        f.write("eula=true\n")
    with open(os.path.join(workdir, "server.properties"), "w") as f:
        f.write("\n".join([
            "online-mode=false", "level-type=minecraft\\:flat", "spawn-protection=0",
            "view-distance=4", "simulation-distance=4",
            # Default 60: an empty server stops ticking, and nothing here ever joins.
            "pause-when-empty-seconds=-1",
            "enable-command-block=false", "sync-chunk-writes=false", "server-port=25598", ""]))
    return notes


# ---------------------------------------------------------------- scenarios

def answer(s, cmd, ok_pattern, bad_pattern=r"Unknown|Incorrect|not found|Could not|failed|error|Error", timeout=20):
    """Send a console command; (True|False|None, line): matched ok, matched bad, or no answer."""
    s.drain()
    s.send(cmd)
    rx_ok, rx_bad = re.compile(ok_pattern), re.compile(bad_pattern)
    end = time.time() + timeout
    while time.time() < end:
        line = s.wait_for(r".", max(0.1, end - time.time()))
        if line is None:
            break
        if rx_ok.search(line):
            return True, line
        if rx_bad.search(line):
            return False, line
    return None, ""


def run(s, results, p):
    def check(name, ok, detail=""):
        results.append((name, bool(ok), detail))
        print(f"[{'PASS' if ok else 'FAIL'}] {name} {detail}", flush=True)

    # Keep only the areas the checks use loaded (forceload caps one call at 256 chunks).
    # The village area is wide: pieces reach ~80 blocks from the start, all of it must be loaded.
    for area in ("0 0 191 47", "288 -16 319 15", "112 112 287 287"):
        s.send(f"forceload add {area}")
    time.sleep(3)

    # commands: the root command reports this jar's version; list reads the SavedData store
    ok, line = answer(s, "sswaystones", r"sswaystones \S+, made with")
    version = p.get("mod_version", "").replace(" ", "")
    check("commands: /sswaystones reports this version", ok and version in line.replace(" ", ""), line[-120:])
    ok, line = answer(s, "sswaystones list", r"[Ww]aystones|\(")
    check("commands: /sswaystones list reads storage", ok, line[-120:])

    # templates: each village waystone piece loads and places on this Minecraft version
    for i, biome in enumerate(VILLAGE_BIOMES):
        x = 40 * i
        ok, line = answer(s, f"place template sswaystones:village/{biome}/waystone {x} -60 20",
                          r"Loaded template|[Pp]laced", r"[Ff]ailed|not found|not loaded|Unknown|Invalid|Incorrect|error")
        check(f"templates: {biome} waystone piece places", ok, line[-120:])

    # block: a command-placed waystone exists and its block entity ticks cleanly for a while
    s.send("setblock 300 -60 0 sswaystones:waystone")
    time.sleep(1)
    check("block: waystone placed by command exists",
          s.test("execute if block 300 -60 0 sswaystones:waystone"))
    time.sleep(5)
    check("block: block entity ticks without errors", s.alive() and not s.fatal, "; ".join(s.fatal[:2]))

    # village: a whole village generates through the injected pools (JigsawPlacerMixin)
    ok, line = answer(s, "place structure minecraft:village_plains 200 -60 200",
                      r"[Gg]enerated|[Pp]laced", r"[Ff]ailed|not loaded|Unknown|Invalid|Incorrect|error", timeout=90)
    check("village: plains village generates with waystone pools injected", ok and s.alive(), line[-120:])


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--jar")
    ap.add_argument("--workdir", default=os.path.join(ROOT, "build", "server-test"))
    ap.add_argument("--boot-timeout", type=int, default=600)
    a = ap.parse_args()
    jar = a.jar or next((j for j in sorted(glob.glob(os.path.join(ROOT, "build", "libs", "*.jar")))
                         if not j.endswith(("-sources.jar", "-dev.jar"))), None)
    if not jar:
        raise SystemExit("no jar: run ./gradlew build first or pass --jar")
    shutil.rmtree(a.workdir, ignore_errors=True)
    p = props()
    notes = setup(a.workdir, jar, p)
    for n in notes:
        print("  " + n, flush=True)

    s = Server(a.workdir)
    results = []
    s.start()
    try:
        init = s.wait_for(r"sswaystones is made with <3", a.boot_timeout)
        results.append(("boot: sswaystones initialized", init is not None, init or ""))
        inject = s.wait_for(r"Injecting waystone village structures|Done \(\d", a.boot_timeout) if init else None
        injected = inject is not None and "Injecting" in inject
        results.append(("boot: village pieces injected", injected, inject or ""))
        done = (s.wait_for(r"Done \(\d", a.boot_timeout) if injected else inject) if init else None
        results.append(("boot: server reached Done", done is not None, done or ""))
        if done:
            run(s, results, p)
    finally:
        s.stop()
    results.append(("no mixin / tick / entrypoint errors in the log", len(s.fatal) == 0,
                    "; ".join(s.fatal[:3])))

    with open(os.path.join(a.workdir, "console.log"), "w") as f:
        f.write("\n".join(s.log))
    failed = [r for r in results if r[1] is False]
    ran = [r for r in results if r[1] is not None]
    # Machine-readable record of this run for README badges (scripts/publish_badges.py).
    with open(os.path.join(a.workdir, "versions.json"), "w") as f:
        json.dump({
            "mod": p.get("mod_version", "").replace(" ", ""), "minecraft": p.get("minecraft_version"),
            "minecraft_tested": TESTED.get("minecraft_tested"),
            "loader": p.get("loader_version"), "fabric_api_compiled": p.get("fabric_api_version"),
            "fabric_api_tested": TESTED.get("fabric_api_tested"), "polymer": p.get("polymer_version"),
            "passed": len(ran) - len(failed), "total": len(ran),
            "sha": os.environ.get("GITHUB_SHA"), "branch": os.environ.get("GITHUB_REF_NAME"),
        }, f, indent=2)
    if failed:
        print("---- last 80 console lines ----")
        print("\n".join(s.log[-80:]))
        print("---- end ----", flush=True)
    md = [f"### Server test: Minecraft {TESTED.get('minecraft_tested')}, sswaystones {p.get('mod_version')}", ""]
    md += [f"- {n}" for n in notes] + ["", "| Check | Result |", "|---|---|"]
    md += [f"| {n} | {'✅' if ok else '❌ ' + d.replace('|', '/')[:200]} |" for n, ok, d in results]
    md += ["", f"**{len(ran) - len(failed)}/{len(ran)} passed**"]
    print("\n".join(md))
    if os.environ.get("GITHUB_STEP_SUMMARY"):
        with open(os.environ["GITHUB_STEP_SUMMARY"], "a") as f:
            f.write("\n".join(md) + "\n")
    sys.exit(1 if failed else 0)


if __name__ == "__main__":
    main()
