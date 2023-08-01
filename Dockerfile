# Use the official Python 3.10 image as the base
FROM python:3.10

# Set the working directory
WORKDIR /app

ENV USE_CUDA=0
ENV TRANSFORMERS_OFFLINE=1

RUN pip install poetry

# Copy the requirements file
COPY requirements.txt /app/requirements.txt
COPY pyproject.toml /app/pyproject.toml
COPY README.md /app/README.md
COPY pyproject.toml poetry.lock* /app/
COPY ./copilot /app/copilot

RUN poetry config virtualenvs.create false \
  && poetry install --no-interaction --no-ansi

# Copy the run file
COPY ./run.py /app/run.py

RUN pip install -e .

# Start the server
CMD ["python", "run.py"]
