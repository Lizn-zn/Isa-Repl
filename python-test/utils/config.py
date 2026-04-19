import os
from pathlib import Path

_REPO_ROOT = Path(__file__).resolve().parents[2]
_DEFAULT_JAR = _REPO_ROOT / "target" / "IsaREPL.jar"

ISA_REPL_PATH = os.getenv("ISA_REPL_PATH", str(_DEFAULT_JAR))
