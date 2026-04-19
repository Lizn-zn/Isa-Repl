import re
from typing import Any


def parse_java_object(parse_result: str) -> tuple[bool, str]:
    """Parse Java gateway string result in format: "True<\\SEP>..." or "False<\\SEP>..."."""
    if "<\\SEP>" in parse_result:
        ok, msg = parse_result.split("<\\SEP>", 1)
        return ok == "True", msg
    return False, f"Failed to parse the result. Got unexpected result: {parse_result}"


def parse_str_output(raw_result: str) -> tuple[bool, str]:
    """Compatibility parser for plain two-part outputs."""
    return parse_java_object(raw_result)


def remove_illegal_variables(text: str) -> str:
    # Remove like "Illegal schematic type variable: ?'a1"
    text = re.sub(r"::\?'[a-zA-Z0-9]+", "", text)
    return text.strip()


def parse_tactic(tactic: str) -> str:
    """Parse tactic from a string that may contain timing information."""
    if "Try this:" in tactic:
        tactic = tactic.split("Try this:", 1)[1].strip()

    if "(" in tactic:
        tactic = tactic.rsplit("(", 1)[0].strip()

    return remove_illegal_variables(tactic)


def shorten_text(text: str, width: int = 30) -> str:
    # width is a soft limit
    text = text.replace("\n", " ")
    if len(text) > width:
        text = text.split(" ")[0:width]
        text = " ".join(text) + "..."
    return text


def clean_whitespace(text: str) -> str:
    text = re.sub(r"\s", " ", text)
    text = re.sub(r" +", " ", text)
    return text.strip()


def parse_hammer_facts_output(raw_output: str) -> tuple[bool, int, list[dict[str, str]], str]:
    result_split = raw_output.split("<\\SEP>")
    success = result_split[0]
    if success == "False":
        error_message = result_split[1] if len(result_split) > 1 else "Unknown error"
        return False, 0, [], error_message

    num = int(result_split[1])
    result_lst = [result.split("<\\INNER_SEP>") for result in result_split[3:] if result]
    facts = [
        {
            "theory": result[0],
            "fact": result[1],
            "fact_definition": result[2].strip(),
        }
        for result in result_lst
        if len(result) >= 3
    ]
    return True, num, facts, ""


def parse_thms_in_parent_output(raw_output: str) -> tuple[bool, int, list[str], str]:
    result_split = raw_output.split("<\\SEP>")
    success = result_split[0]
    if success == "False":
        error_message = result_split[1] if len(result_split) > 1 else "Unknown error"
        return False, 0, [], error_message

    result_lst = result_split[1:]
    return True, len(result_lst), result_lst, ""


def parse_dependent_thms_output(raw_output: str) -> tuple[bool, int, list[dict[str, str]], str]:
    result_split = raw_output.split("<\\SEP>")
    success = result_split[0]
    if success == "False":
        error_message = result_split[1] if len(result_split) > 1 else "Unknown error"
        return False, 0, [], error_message

    parsed = []
    for result in result_split[1:]:
        parts = result.split("<\\INNER_SEP>")
        if len(parts) >= 2:
            parsed.append({"theory": parts[0], "fact": parts[1]})

    return True, len(parsed), parsed, ""


def parse_hammer_prove_output(raw_output: str) -> tuple[bool, int, list[str], str]:
    result_split = raw_output.split("<\\SEP>")
    success = result_split[0]
    if success == "False":
        error_message = result_split[1] if len(result_split) > 1 else "Unknown error"
        return False, 0, [], error_message

    result_lst = result_split[1:]
    return True, len(result_lst), result_lst, ""
