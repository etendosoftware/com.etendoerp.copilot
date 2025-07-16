# Stage 1: Generate uv.lock and requirements.txt from pyproject.toml
FROM python:3.12.9-slim AS requirements-stage
WORKDIR /tmp

# Install necessary tools
RUN apt update && apt install -y curl
RUN curl -Ls https://astral.sh/uv/install.sh | sh
ENV PATH="/root/.local/bin:$PATH"

# Copy project definition
COPY ./pyproject.toml /tmp/

# Generate lockfile and export requirements.txt without hashes
RUN uv export --no-hashes --format=requirements-txt --output-file=requirements.txt

# Stage 2: Final application stage
FROM python:3.12.9
RUN apt update && apt install -y libzbar0 curl

# Install uv
RUN curl -Ls https://astral.sh/uv/install.sh | sh
ENV PATH="/root/.local/bin:$PATH"

# Create virtual environment using uv
RUN mkdir -p /venv && cd /venv && uv venv

# Set working directory
WORKDIR /app

# Copy dependencies from the build stage
COPY --from=requirements-stage /tmp/uv.lock /app/uv.lock
COPY --from=requirements-stage /tmp/requirements.txt /app/requirements.txt

# Copy source code
COPY ./copilot /app/copilot
COPY ./tools /app/tools
COPY ./run.py /app/run.py
COPY ./tools_config.json /app/tools_config.json
COPY README.md /app/README.md

# Run: install dependencies with uv and launch the app
CMD ["sh", "-c", ". /venv/.venv/bin/activate && uv sync && python run.py"]
