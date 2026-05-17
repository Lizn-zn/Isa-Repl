import os
import re
import subprocess
import time

import pytest
from py4j.java_gateway import JavaGateway, GatewayParameters, JavaObject

PORT = 25556
ISABELLE_HOME = os.environ.get(
    "ISABELLE_HOME", os.path.expanduser("~/verification/isabelle/")
)
# Set L4V_PATH to your local l4v directory. See README for details.
L4V_PATH = os.environ.get("L4V_PATH", "")

needs_l4v: pytest.MarkDecorator = pytest.mark.skipif(not L4V_PATH, reason="L4V_PATH not set")

THEORY_TEMPLATE = """theory Test
    imports {session}.{theory_name}
begin
"""

type IsaReplJava = JavaObject

# ---------------------------------------------------------------------------
# JVM lifecycle
# ---------------------------------------------------------------------------

def run_jar_file(jar_path, port=PORT):
    env = os.environ.copy()
    env["ISABELLE_HOME"] = ISABELLE_HOME
    process = subprocess.Popen(["java", "-jar", jar_path, str(port)], env=env)
    time.sleep(2)
    return process


def get_repl(port=PORT) -> IsaReplJava:
    gateway = JavaGateway(
        gateway_parameters=GatewayParameters(port=port, auto_convert=True)
    )
    return gateway.entry_point


# ---------------------------------------------------------------------------
# result parsing
# ---------------------------------------------------------------------------

def split_result(result):
    parts = result.split("<\\SEP>", 1)
    if len(parts) != 2:
        raise AssertionError(f"Malformed result: {result}")
    return parts[0] == "True", parts[1]


# ---------------------------------------------------------------------------
# Isar proof helpers
# ---------------------------------------------------------------------------

def is_comment(isar_command):
    stripped = isar_command.strip()
    return stripped.startswith("(*") and stripped.endswith("*)")


def delete_comments(isar_commands):
    return [c for c in isar_commands if not is_comment(c)]


def replaced_by_sorry(isar_commands):
    keywords = [
        "apply", "supply", "subgoal", "using", "unfolding",
        "proof", "qed", "done",
        "{", "}", "next", "note",
        "let", "write", "fix", "assume", "then",
        "have", "show",
        "from", "with",
        "also", "finally", "moreover", "ultimately",
        "presume", "define", "consider", "obtain", "case",
        "typ", "term", "prop", "thm", "print_statement",
        "apply_end", "defer", "prefer",
        "back", "oops", "hence", "thus", ".", "..", "and",
        "include", "including", "is", "interpret",
        "by",
    ]
    depth = 0
    in_notepad = False
    notepad_depth = -1
    replaced_commands = []
    i, length = 0, len(isar_commands)
    while i < length:
        if isar_commands[i].endswith("begin"):
            depth += 1
        if isar_commands[i].strip() == "end":
            if depth == notepad_depth and in_notepad:
                in_notepad = False
            depth -= 1
        if isar_commands[i].startswith("notepad"):
            in_notepad = not in_notepad
            notepad_depth = depth
        if not in_notepad:
            if any(re.split(r"[ ()]+", isar_commands[i].strip())[0] == kw for kw in keywords):
                while i < length and any(
                    re.split(r"[ ()\n]+", isar_commands[i].strip())[0] == kw for kw in keywords
                ):
                    i += 1
                replaced_commands.append("sorry")
            else:
                replaced_commands.append(isar_commands[i])
                i += 1
        else:
            replaced_commands.append(isar_commands[i])
            i += 1
    return replaced_commands
