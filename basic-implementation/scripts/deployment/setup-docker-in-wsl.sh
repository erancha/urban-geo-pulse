#!/bin/bash
set -e

echo "Starting Docker installation in WSL Ubuntu..."

# Check if running as root
if [ "$EUID" -ne 0 ]; then 
    echo "Please run as root (use sudo)"
    exit 1
fi

# Enable systemd
echo "Enabling systemd..."
cat > /etc/wsl.conf << EOF
[boot]
systemd=true
EOF

if ! ps -p 1 -o comm= | grep -q "systemd"; then
    echo "Systemd is not running as PID 1. Please:"
    echo "1. Exit WSL: 'exit'"
    echo "2. In PowerShell: 'wsl --shutdown'"
    echo "3. Restart WSL and run this script again"
    exit 1
fi

# Complete cleanup of old installations
echo "Cleaning up any existing Docker installations..."
systemctl stop docker.service docker.socket containerd.service || true
systemctl disable docker.service docker.socket containerd.service || true
apt-get remove -y docker docker-engine docker.io containerd runc || true
apt-get purge -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin || true
apt-get autoremove -y
rm -rf /var/lib/docker /etc/docker /var/run/docker.sock /var/run/docker.pid
rm -rf /var/lib/containerd /etc/containerd/config.toml

# Update package index and install prerequisites
echo "Installing prerequisites..."
apt-get update
apt-get install -y \
    apt-transport-https \
    ca-certificates \
    curl \
    gnupg \
    lsb-release

# Add Docker's official GPG key
echo "Adding Docker's GPG key..."
mkdir -p /etc/apt/keyrings
curl -fsSL https://download.docker.com/linux/ubuntu/gpg | gpg --dearmor -o /etc/apt/keyrings/docker.gpg
chmod a+r /etc/apt/keyrings/docker.gpg

# Set up the Docker repository
echo "Setting up Docker repository..."
echo \
  "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] https://download.docker.com/linux/ubuntu \
  $(lsb_release -cs) stable" | tee /etc/apt/sources.list.d/docker.list > /dev/null

# Install Docker Engine
echo "Installing Docker Engine..."
apt-get update
apt-get install -y \
    docker-ce \
    docker-ce-cli \
    containerd.io \
    docker-compose-plugin

# Configure containerd
echo "Configuring containerd..."
mkdir -p /etc/containerd
containerd config default | tee /etc/containerd/config.toml > /dev/null
sed -i 's/SystemdCgroup = false/SystemdCgroup = true/g' /etc/containerd/config.toml

# Start and enable containerd
echo "Starting containerd..."
systemctl enable --now containerd
systemctl restart containerd

# Configure Docker daemon
echo "Configuring Docker daemon..."
mkdir -p /etc/docker
cat > /etc/docker/daemon.json << EOF
{
  "log-driver": "json-file",
  "log-opts": {
    "max-size": "10m",
    "max-file": "3"
  }
}
EOF

# Start and enable Docker
echo "Starting Docker service..."
systemctl enable docker
systemctl start docker

# Add current user to docker group
if [ -n "$SUDO_USER" ]; then
    usermod -aG docker "$SUDO_USER"
    echo "Added user $SUDO_USER to docker group"
fi

# Run ensure-docker.sh to verify everything
echo "Verifying Docker setup..."
cp ensure-docker.sh /usr/local/bin/ensure-docker
chmod +x /usr/local/bin/ensure-docker
/usr/local/bin/ensure-docker

echo "Docker installation complete!"
echo "Please exit WSL and restart it for all changes to take effect:"
echo "1. Exit WSL: 'exit'"
echo "2. In PowerShell: 'wsl --shutdown'"
echo "3. Restart WSL: 'wsl -d Ubuntu'"
