# To use this script:
# In your WSL Ubuntu session, navigate to the deployment directory: 
#     cd /mnt/c/Projects/IntelliJ/urban-geo-pulse/basic-implementation/scripts/deployment
#     chmod +x setup-docker-in-wsl.sh
#     sudo ./setup-docker-in-wsl.sh

#!/bin/bash
set -e

echo "Starting Docker installation in WSL Ubuntu..."

# Check if running as root
if [ "$EUID" -ne 0 ]; then 
    echo "Please run as root (use sudo)"
    exit 1
fi

# Remove any old Docker installations
echo "Removing old Docker installations if they exist..."
apt-get remove -y docker docker-engine docker.io containerd runc || true

# Update package index and install prerequisites
echo "Installing prerequisites..."
apt-get update
apt-get install -y \
    ca-certificates \
    curl \
    gnupg \
    lsb-release

# Add Docker's official GPG key
echo "Adding Docker's GPG key..."
mkdir -m 0755 -p /etc/apt/keyrings
curl -fsSL https://download.docker.com/linux/ubuntu/gpg | gpg --dearmor -o /etc/apt/keyrings/docker.gpg

# Set up the Docker repository
echo "Setting up Docker repository..."
echo \
  "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] https://download.docker.com/linux/ubuntu \
  $(lsb_release -cs) stable" | tee /etc/apt/sources.list.d/docker.list > /dev/null

# Install Docker Engine and Docker Compose
echo "Installing Docker Engine and Docker Compose..."
apt-get update
apt-get install -y \
    docker-ce \
    docker-ce-cli \
    containerd.io \
    docker-buildx-plugin \
    docker-compose-plugin

# Start Docker service
echo "Starting Docker service..."
service docker start

# Add current user to docker group
if [ -n "$SUDO_USER" ]; then
    echo "Adding user $SUDO_USER to docker group..."
    usermod -aG docker "$SUDO_USER"
    echo "NOTE: You'll need to log out and back in for the group changes to take effect"
fi

# Configure Docker to start on WSL boot
echo "Configuring Docker to start on WSL boot..."
if ! grep -q "service docker start" /etc/wsl.conf 2>/dev/null; then
    echo -e "[boot]\ncommand=\"service docker start\"" >> /etc/wsl.conf
fi

# Verify installation
echo "Verifying Docker installation..."
docker --version
docker compose version

echo "Docker installation complete!"
echo "To verify everything is working, try running: docker run hello-world"
echo "If you get a permission error, log out of WSL and log back in for group changes to take effect"
