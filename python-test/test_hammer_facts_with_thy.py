import os
import re
from py4j.java_gateway import JavaGateway, GatewayParameters


# Connect to the JVM
gateway = JavaGateway(gateway_parameters=GatewayParameters(port=25555, auto_convert=True))

# Get the IsaREPL application
isa_repl = gateway.entry_point

# Initialize REPL with a theory file
theory_file = os.path.abspath("/path/to/l4v/proof/invariant-abstract/Deterministic_AI.thy")

isa_repl._initializeRepl(theory_file, "/path/to/l4v", "AInvs", ["/path/to/l4v"])

with open(theory_file, "r", encoding="utf-8") as f:
    content = f.read()

# Compile the theory file
target = "lemma sofl_test: \\<open>sint x + sint y = sint (x + y) \\<longleftrightarrow> drop_bit (size x - 1) ((x + y XOR x) AND (x + y XOR y)) = 0\\<close> for x y :: \\<open>'a::len word\\<close>"
steps = isa_repl._parse_to_steps(content).split("<\\SEP>")
# Add a proof step
for i, step in enumerate(steps):
    # print(i, step)
    if "lemma no_children_empty_desc" in step:
        target = step
        break


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
        if i == 210:
            pass
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
                to_be_replaced = []
                while i < length and any(
                        [re.split(r'[ ()\n]+', isar_commands[i].strip())[0] == keyword for keyword in keywords]):
                    to_be_replaced.append(isar_commands[i])
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
    result = []
    for command in isar_commands:
        if is_comment(command):
            continue
        result.append(command)
    return result


steps = delete_comments(steps)
steps = replaced_by_sorry(steps)

# all_steps_str = "\n".join(steps[1:])
# print(isa_repl._step(all_steps_str))

# for i, step in enumerate(steps):
#     print(i, step)

plain = False

if plain:
    for i, step in enumerate(steps):
        print(i, step, "\n", isa_repl._step(steps[i]))
else:
    i, unprocessed, attached_num = 1, '', 0
    while i < len(steps):
        result = isa_repl._step(unprocessed + steps[i])
        if target in unprocessed + steps[i]:
            print(isa_repl._mash_state_relearn())
            result = isa_repl._extract_hammer_facts_with_thy_names("mesh")
            result_lst = result.split("<\\SEP>")
            print(result_lst[:10])
            break
        if "False<\\SEP>" in result:
            unprocessed += steps[i] + '\n'
            attached_num += 1
        else:
            # print(i, unprocessed + steps[i], "\n", result)
            unprocessed = ''
            attached_num = 0
        i += 1

isa_repl._exit()