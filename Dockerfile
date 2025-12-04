FROM python:3.12.9-slim
RUN apt update && \
    apt install -y curl libzbar0 nodejs build-essential && \
    curl -fsSL https://deb.nodesource.com/setup_lts.x | bash - && \
    apt install -y nodejs && \
    npm install -g npm@latest && \
    rm -rf /var/lib/apt/lists/*
# Install uv
RUN curl -Ls https://astral.sh/uv/install.sh | sh
# Add uv to PATH
ENV PATH="/root/.local/bin:$PATH"
# Create virtual environment outside of /app
RUN mkdir -p /venv && cd /venv && uv venv \
    && /venv/.venv/bin/python -m ensurepip --upgrade \
    && /venv/.venv/bin/pip3 install --upgrade pip
# Create folders
RUN mkdir /checkpoints
# Set working directory
WORKDIR /app
RUN mkdir vectordbs
# Install Python dependencies
COPY ./tools_deps.toml /app-deps/tools_deps.toml
COPY ./requirements.txt /app-deps/requirements.txt
COPY ./local_setup.py /app-deps/local_setup.py
RUN ["sh", "-c", ". /venv/.venv/bin/activate && uv pip install -r /app-deps/requirements.txt && python /app-deps/local_setup.py"]
# Entrypoint: install dependencies + run script
CMD ["sh", "-c", ". /venv/.venv/bin/activate && python run.py"]
