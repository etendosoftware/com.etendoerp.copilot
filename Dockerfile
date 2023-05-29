# Use the official Python 3.10 image as the base
FROM python:3.10

# Set the working directory
WORKDIR /app

# Copy the requirements file
COPY requirements.txt /app/requirements.txt

# Install the required packages
RUN pip install --no-cache-dir --upgrade -r /app/requirements.txt

# Copy the Python files
COPY ./copilot /app/copilot

# Start the server
CMD ["python", "run.py"]
