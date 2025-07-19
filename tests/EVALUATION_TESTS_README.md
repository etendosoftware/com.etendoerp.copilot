# Evaluation Package Tests

This directory contains comprehensive tests for the evaluation package to ensure your refactoring doesn't break existing functionality.

## 📁 Test Files

- `test_evaluation_schemas.py` - Tests for data schemas (Message, Conversation)
- `test_evaluation_utils.py` - Tests for utility functions (calc_md5, validation, etc.)
- `test_evaluation_execute.py` - Tests for execution logic (exec_agent function)
- `test_evaluation_bulk_tasks.py` - Tests for bulk task evaluation functionality
- `test_evaluation_gen_variants.py` - Tests for variant generation logic
- `run_evaluation_tests.py` - Main test runner script
- `validate_refactor.py` - Pre/post refactor validation script

## 🚀 Quick Start

### Before Refactoring

1. **Run all tests to establish baseline:**
   ```bash
   cd tests
   python run_evaluation_tests.py
   ```

2. **Capture pre-refactor state:**
   ```bash
   python validate_refactor.py before
   ```

### After Refactoring

3. **Validate your refactor:**
   ```bash
   python validate_refactor.py after
   ```

4. **Compare results:**
   ```bash
   python validate_refactor.py compare
   ```

## 🧪 Individual Test Commands

### Run specific test file:
```bash
python run_evaluation_tests.py --file test_evaluation_schemas.py
```

### Run quick smoke test:
```bash
python run_evaluation_tests.py --smoke
```

### Run with pytest directly:
```bash
pytest test_evaluation_schemas.py -v
pytest test_evaluation_utils.py -v
pytest test_evaluation_execute.py -v
pytest test_evaluation_bulk_tasks.py -v
pytest test_evaluation_gen_variants.py -v
```

## 📊 Understanding Test Results

### ✅ Success Indicators
- All tests pass (green checkmarks)
- No import errors
- All assertions pass

### ❌ Failure Indicators
- Red X marks or FAILED status
- Import errors (missing dependencies)
- Assertion failures
- Runtime exceptions

### ⚠️ Warnings to Watch
- Deprecated function usage
- Missing optional dependencies (NLTK, Levenshtein)
- Configuration issues

## 🔧 Test Coverage

### Schemas Module (`test_evaluation_schemas.py`)
- ✅ Message creation and validation
- ✅ Conversation structure testing
- ✅ Tool call handling
- ✅ Multimodal content support
- ✅ Date formatting validation

### Utils Module (`test_evaluation_utils.py`)
- ✅ MD5 hash calculation consistency
- ✅ Dataset folder validation
- ✅ Supabase integration (mocked)
- ✅ Error handling
- ✅ Unicode support

### Execute Module (`test_evaluation_execute.py`)
- ✅ Agent execution logic
- ✅ Parameter validation
- ✅ Authentication handling
- ✅ Save vs evaluate modes
- ✅ Error scenarios

### Bulk Tasks Module (`test_evaluation_bulk_tasks.py`)
- ✅ Database configuration
- ✅ CSV processing
- ✅ Template formatting
- ✅ API integration (mocked)
- ✅ Task generation logic

### Gen Variants Module (`test_evaluation_gen_variants.py`)
- ✅ Text transformation
- ✅ Variant generation algorithms
- ✅ File operations
- ✅ Metrics calculation
- ✅ Optional dependency handling

## 🐍 Python Environment Requirements

Make sure you have the required packages:

```bash
pip install pytest pandas requests psycopg2-binary
```

Optional packages (for full functionality):
```bash
pip install nltk python-levenshtein
```

## 🏗️ Refactoring Workflow

1. **Establish Baseline** 📋
   ```bash
   python validate_refactor.py before
   ```

2. **Perform Your Refactoring** 🔨
   - Refactor your evaluation code
   - Maintain public API compatibility
   - Keep the same functionality

3. **Validate Changes** ✅
   ```bash
   python validate_refactor.py after
   python validate_refactor.py compare
   ```

4. **Review Results** 📊
   - Check `tests/refactor_validation_results/` for detailed reports
   - Ensure all tests still pass
   - Review any performance changes

## 🚨 Common Issues and Solutions

### Import Errors
- **Problem**: `ModuleNotFoundError: No module named 'evaluation.schemas'`
- **Solution**: Make sure you're running from the correct directory and Python path is set

### Missing Dependencies
- **Problem**: Tests skip due to missing packages (NLTK, Levenshtein)
- **Solution**: Install optional dependencies or ignore skipped tests

### Database Connection Errors
- **Problem**: PostgreSQL connection failures in bulk tasks tests
- **Solution**: Tests use mocking - actual DB not needed. Check mock configuration.

### Timeout Issues
- **Problem**: Tests take too long or timeout
- **Solution**: Use `--quick` flag or check for infinite loops in your code

## 📈 Test Metrics

The validation script tracks:
- ✅ **Pass/Fail Status** - Whether tests pass before/after
- ⏱️ **Execution Time** - Performance impact of refactoring
- 🔍 **Detailed Logs** - Full output for debugging
- 📊 **Comparison Report** - Side-by-side analysis

## 🎯 Success Criteria

Your refactor is successful when:
1. ✅ All tests that passed before still pass after
2. ✅ No new critical errors introduced
3. ✅ Performance doesn't significantly degrade
4. ✅ All public APIs remain functional

## 📞 Troubleshooting

If tests fail after refactoring:

1. **Check the comparison report** in `refactor_validation_results/`
2. **Run individual test files** to isolate issues
3. **Compare before/after logs** to identify breaking changes
4. **Use pytest's verbose mode** for detailed error information

```bash
pytest test_evaluation_schemas.py -v -s --tb=long
```

Good luck with your refactoring! 🚀
