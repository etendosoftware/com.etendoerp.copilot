FROM python:3.12.9-slim
RUN apt update && \
    apt install -y curl libzbar0 nodejs && \
    curl -fsSL https://deb.nodesource.com/setup_lts.x | bash - && \
    apt install -y nodejs && \
    npm install -g npm@latest && \
    rm -rf /var/lib/apt/lists/*

# Install uv
RUN curl -Ls https://astral.sh/uv/install.sh | sh
ENV PATH="/root/.local/bin:$PATH"

# Create virtual environment using uv
RUN mkdir -p /venv && cd /venv && uv venv \
    && /venv/.venv/bin/python -m ensurepip --upgrade \
    && /venv/.venv/bin/pip3 install --upgrade pip

# Set working directory
WORKDIR /app

# Copy source code
COPY ./copilot /app/copilot
COPY ./tools /app/tools
COPY ./run.py /app/run.py
COPY ./tools_deps.toml /app/tools_deps.toml
COPY ./uv.lock /app/uv.lock
COPY ./requirements.txt /app/requirements.txt
COPY ./pyproject.toml /app/pyproject.toml
COPY README.md /app/README.md
COPY ./local_setup.py /app/local_setup.py

# Install Python dependencies
RUN ["sh", "-c", ". /venv/.venv/bin/activate && uv pip install -r requirements.txt && python local_setup.py --empty-tool-deps"]

# Run: install dependencies with uv and launch the app
CMD ["sh", "-c", ". /venv/.venv/bin/activate && python run.py"]
