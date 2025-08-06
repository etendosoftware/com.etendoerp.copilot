FROM python:3.12.9-slim
RUN apt update && \
    apt install -y curl libzbar0 nodejs && \
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
WORKDIR /app
# Entrypoint: install dependencies + run script
CMD ["sh", "-c", ". /venv/.venv/bin/activate && uv pip install --link-mode=copy -r requirements.txt && python run.py"]