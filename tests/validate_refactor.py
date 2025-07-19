"""
Pre-refactor and Post-refactor validation script

This script validates that your refactor did not break existing functionality
by running the same tests before and after refactoring.
"""

import json
import subprocess
import sys
import time
from datetime import datetime
from pathlib import Path


class RefactorValidator:
    """Validates refactoring by comparing test results before and after"""

    def __init__(self):
        self.test_dir = Path(__file__).parent
        self.results_dir = self.test_dir / "refactor_validation_results"
        self.results_dir.mkdir(exist_ok=True)

    def run_tests_and_capture_results(self, phase="before"):
        """Run all evaluation tests and capture results"""
        print(f"🧪 Running tests {phase} refactor...")

        # Run the test runner and capture output
        cmd = [
            sys.executable,
            str(self.test_dir / "run_evaluation_tests.py"),
            "--quick",  # Don't stop on first failure
        ]

        start_time = time.time()

        try:
            result = subprocess.run(cmd, capture_output=True, text=True, timeout=300)  # 5 minute timeout

            end_time = time.time()
            execution_time = end_time - start_time

            # Parse results
            test_results = {
                "phase": phase,
                "timestamp": datetime.now().isoformat(),
                "execution_time": execution_time,
                "return_code": result.returncode,
                "stdout": result.stdout,
                "stderr": result.stderr,
                "success": result.returncode == 0,
            }

            # Save results
            results_file = self.results_dir / f"test_results_{phase}.json"
            with open(results_file, "w") as f:
                json.dump(test_results, f, indent=2)

            print(f"✅ Tests {phase} refactor completed in {execution_time:.2f}s")
            print(f"📁 Results saved to: {results_file}")

            return test_results

        except subprocess.TimeoutExpired:
            print(f"❌ Tests {phase} refactor timed out!")
            return None
        except Exception as e:
            print(f"❌ Error running tests {phase} refactor: {e}")
            return None

    def compare_results(self, before_results, after_results):
        """Compare test results before and after refactoring"""
        print("\n🔍 Comparing Results...")
        print("=" * 50)

        if not before_results or not after_results:
            print("❌ Cannot compare - missing results")
            return False

        # Compare success status
        before_success = before_results["success"]
        after_success = after_results["success"]

        print(f"Before refactor: {'✅ PASS' if before_success else '❌ FAIL'}")
        print(f"After refactor:  {'✅ PASS' if after_success else '❌ FAIL'}")

        # Compare execution time
        time_before = before_results["execution_time"]
        time_after = after_results["execution_time"]
        time_diff = time_after - time_before

        print("\nExecution time:")
        print(f"Before: {time_before:.2f}s")
        print(f"After:  {time_after:.2f}s")
        print(f"Difference: {time_diff:+.2f}s")

        # Overall assessment
        print("\n📊 Overall Assessment:")

        if after_success and before_success:
            print("🎉 SUCCESS: All tests still pass after refactoring!")
            if time_diff < 0:
                print(f"🚀 BONUS: Tests run {abs(time_diff):.2f}s faster!")
            return True
        elif after_success and not before_success:
            print("🎉 IMPROVEMENT: Tests now pass (they failed before)!")
            return True
        elif not after_success and before_success:
            print("❌ REGRESSION: Tests now fail (they passed before)!")
            print("🔧 Please check your refactoring - something broke.")
            return False
        else:
            print("⚠️  Both before and after failed - check baseline tests first")
            return False

    def save_comparison_report(self, before_results, after_results):
        """Save a detailed comparison report"""
        if not before_results or not after_results:
            return

        comparison = {
            "comparison_timestamp": datetime.now().isoformat(),
            "before": before_results,
            "after": after_results,
            "summary": {
                "success_before": before_results["success"],
                "success_after": after_results["success"],
                "time_difference": after_results["execution_time"] - before_results["execution_time"],
                "refactor_successful": (
                    after_results["success"] and (before_results["success"] or after_results["success"])
                ),
            },
        }

        report_file = self.results_dir / "refactor_comparison_report.json"
        with open(report_file, "w") as f:
            json.dump(comparison, f, indent=2)

        print(f"📋 Detailed comparison report saved to: {report_file}")


def main():
    """Main validation workflow"""
    import argparse

    parser = argparse.ArgumentParser(description="Validate refactoring")
    parser.add_argument("phase", choices=["before", "after", "compare"], help="Phase of validation")

    args = parser.parse_args()
    validator = RefactorValidator()

    if args.phase == "before":
        print("🏁 Starting PRE-REFACTOR validation...")
        results = validator.run_tests_and_capture_results("before")
        if results:
            if results["success"]:
                print("✅ Pre-refactor tests PASSED - safe to proceed with refactoring!")
            else:
                print("❌ Pre-refactor tests FAILED - fix these issues before refactoring!")

    elif args.phase == "after":
        print("🏁 Starting POST-REFACTOR validation...")
        results = validator.run_tests_and_capture_results("after")
        if results:
            if results["success"]:
                print("✅ Post-refactor tests PASSED!")
            else:
                print("❌ Post-refactor tests FAILED - check your refactoring!")

    elif args.phase == "compare":
        print("🏁 Comparing PRE and POST refactor results...")

        # Load previous results
        before_file = validator.results_dir / "test_results_before.json"
        after_file = validator.results_dir / "test_results_after.json"

        if not before_file.exists():
            print("❌ No 'before' results found. Run with 'before' phase first.")
            return False

        if not after_file.exists():
            print("❌ No 'after' results found. Run with 'after' phase first.")
            return False

        with open(before_file) as f:
            before_results = json.load(f)

        with open(after_file) as f:
            after_results = json.load(f)

        success = validator.compare_results(before_results, after_results)
        validator.save_comparison_report(before_results, after_results)

        return success


if __name__ == "__main__":
    success = main()
    sys.exit(0 if success else 1)
