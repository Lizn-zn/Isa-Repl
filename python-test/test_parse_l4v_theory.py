import os
import re

from utils.repl import IsaRepl


def replaced_by_sorry(isar_commands: list[str]):
    keywords = [
        "apply", "supply", "subgoal", "using", "unfolding",
        "proof", "qed", "done",
        "{", "}", "next", "note",
        "let", "write", "fix", "assume", "then",
        "have", "show",
        "fix", "assume", "then", "have", "show", "using", "unfolding",
        "proof", "qed", "next", "note", "let", "write",
        "from", "with",
        "also", "finally", "moreover", "ultimately",
        "presume", "define", "consider", "obtain", "case",
        "typ", "term", "prop", "thm", "print_statement",
        "apply", "apply_end", "supply", "subgoal", "defer", "prefer",
        "back", "done", "oops", "hence", "thus", ".", "..", "and",
        "include", "including", "is", "interpret",
        "by"
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
            if any([re.split(r'[ ()]+', isar_commands[i].strip())[0] == keyword for keyword in keywords]):
                while i < length and any(
                        [re.split(r'[ ()\n]+', isar_commands[i].strip())[0] == keyword for keyword in keywords]):
                    i += 1
                replaced_commands.append("sorry")
            else:
                replaced_commands.append(isar_commands[i])
                i += 1
        else:
            replaced_commands.append(isar_commands[i])
            i += 1
    return replaced_commands


def is_comment(isar_command: str):
    stripped = isar_command.strip()
    return stripped.startswith("(*") and stripped.endswith("*)")


def delete_comments(isar_commands: list[str]):
    return [c for c in isar_commands if not is_comment(c)]


def main():
    theory_file = os.path.abspath(
        "/home/hbd/verification/l4v/proof/invariant-abstract/Deterministic_AI.thy"
    )
    working_dir = "/home/hbd/verification/l4v"
    session = "AInvs"
    session_dirs = ["/home/hbd/verification/l4v"]

    with open(theory_file, "r", encoding="utf-8") as f:
        content = f.read()

    with IsaRepl(port=25563, create_port=True) as isa_repl:
        isa_repl.initialize(theory_file, working_dir, session, session_dirs)

        ok, steps = isa_repl.parse(content)
        if not ok:
            raise ValueError("Failed to parse theory")

        for i, step in enumerate(steps):
            print(i, step)

        steps = delete_comments(steps)
        # steps = replaced_by_sorry(steps)

        for i, step in enumerate(steps):
            print(i, step)

        plain = False
        if plain:
            for i, step in enumerate(steps):
                ok, msg = isa_repl.step(step)
                print(i, step, "\n", ok, msg)
        else:
            i, unprocessed = 1, ""
            while i < len(steps):
                ok, msg = isa_repl.step(unprocessed + steps[i])
                if not ok:
                    unprocessed += steps[i] + "\n"
                else:
                    print(i, unprocessed + steps[i], "\n", ok, msg)
                    unprocessed = ""
                i += 1


if __name__ == "__main__":
    main()
