"""
Comprehensive test runner for evaluation package

This script runs all tests for the evaluation package to validate functionality
before and after refactoring.
"""

import sys
from pathlib import Path

import pytest


def run_evaluation_tests():
    """Run all evaluation-related tests"""

    # Get the test directory
    test_dir = Path(__file__).parent

    # Define test files for evaluation package
    evaluation_test_files = [
        "test_evaluation_schemas.py",
        "test_evaluation_utils.py",
        "test_evaluation_execute.py",
        "test_evaluation_bulk_tasks.py",
        "test_evaluation_gen_variants.py",
    ]

    print("🧪 Running Evaluation Package Tests")
    print("=" * 50)

    # Check which test files exist
    existing_tests = []
    for test_file in evaluation_test_files:
        test_path = test_dir / test_file
        if test_path.exists():
            existing_tests.append(str(test_path))
            print(f"✅ Found: {test_file}")
        else:
            print(f"⚠️  Missing: {test_file}")

    if not existing_tests:
        print("❌ No evaluation test files found!")
        return False

    print(f"\n🚀 Running {len(existing_tests)} test files...")
    print("-" * 50)

    # Run pytest with specific configuration
    pytest_args = [
        "-v",  # Verbose output
        "--tb=short",  # Short traceback format
        "--strict-markers",  # Strict marker checking
        "-x",  # Stop on first failure (remove this if you want to see all failures)
        "--disable-warnings",  # Disable warnings for cleaner output
    ] + existing_tests

    try:
        result = pytest.main(pytest_args)

        print("\n" + "=" * 50)
        if result == 0:
            print("🎉 All evaluation tests PASSED!")
            print("✅ Your evaluation package is ready for refactoring.")
        else:
            print("❌ Some tests FAILED!")
            print("🔧 Please fix failing tests before refactoring.")

        return result == 0

    except Exception as e:
        print(f"❌ Error running tests: {e}")
        return False


def run_specific_test_file(test_file_name):
    """Run a specific test file"""
    test_dir = Path(__file__).parent
    test_path = test_dir / test_file_name

    if not test_path.exists():
        print(f"❌ Test file not found: {test_file_name}")
        return False

    print(f"🧪 Running specific test: {test_file_name}")
    print("-" * 50)

    result = pytest.main(["-v", str(test_path)])
    return result == 0


def run_quick_smoke_test():
    """Run a quick smoke test to verify basic functionality"""
    print("🚀 Running Quick Smoke Test for Evaluation Package")
    print("-" * 50)

    # Test basic imports
    try:
        # Test schema imports
        sys.path.insert(0, str(Path(__file__).parent.parent / "evaluation"))
        from schemas import Conversation, Message

        print("✅ Schema imports successful")

        # Test basic schema functionality
        msg = Message(role="USER", content="Test")
        assert msg.role == "USER"
        print("✅ Basic schema functionality working")

        return True

    except ImportError as e:
        print(f"❌ Import error: {e}")
        return False
    except Exception as e:
        print(f"❌ Unexpected error: {e}")
        return False


if __name__ == "__main__":
    import argparse

    parser = argparse.ArgumentParser(description="Run evaluation package tests")
    parser.add_argument("--file", help="Run specific test file")
    parser.add_argument("--smoke", action="store_true", help="Run quick smoke test only")
    parser.add_argument("--quick", action="store_true", help="Run without stopping on first failure")

    args = parser.parse_args()

    if args.smoke:
        success = run_quick_smoke_test()
    elif args.file:
        success = run_specific_test_file(args.file)
    else:
        success = run_evaluation_tests()

    # Exit with appropriate code
    sys.exit(0 if success else 1)
