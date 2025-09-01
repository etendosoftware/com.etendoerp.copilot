"""Memory profiling helpers moved out from routes to a dedicated utility.

This module exposes small helpers to start tracemalloc snapshots and report
memory and object growth. Import this module only when you want to run
profiling; the functions are opt-in and may require the optional
``objgraph`` package to be installed.
"""

import gc
import tracemalloc

from copilot.baseutils.logging_envvar import copilot_debug

try:
    import objgraph
except Exception:  # pragma: no cover - optional dependency
    objgraph = None


def start_memory_profiling():
    """Start tracemalloc and return an initial snapshot.

    Returns:
        tracemalloc.Snapshot: initial memory snapshot
    """
    copilot_debug(">>> Starting memory tracing...")
    if not tracemalloc.is_tracing():
        tracemalloc.start()
    return tracemalloc.take_snapshot()


def report_memory_growth(snapshot_before):
    """Compare current memory usage with ``snapshot_before`` and print a short report."""
    snapshot_after = tracemalloc.take_snapshot()

    # 1. Memory growth by source line
    copilot_debug("\n--- Memory growth (tracemalloc) ---")
    top_stats = snapshot_after.compare_to(snapshot_before, "lineno")
    for stat in top_stats[:5]:
        copilot_debug(stat)

    # 2. Object growth, if objgraph is available
    gc.collect()
    copilot_debug("\n--- Object growth (objgraph) ---")
    if objgraph is not None:
        objgraph.show_growth(limit=10)
    else:
        copilot_debug("objgraph not installed; skipping object growth report.")

    copilot_debug(">>> Memory report finished.")
