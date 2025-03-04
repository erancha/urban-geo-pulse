#!/bin/bash
set -e

echo "Creating shell scripts with proper Unix line endings..."

# First, install dos2unix if not present
if ! command -v dos2unix &> /dev/null; then
    echo "Installing dos2unix..."
    sudo apt-get update
    sudo apt-get install -y dos2unix
fi

# Fix line endings in .env files
echo "Fixing line endings in .env files..."
dos2unix .env activity-aggregator-vars.env 2>/dev/null || true

# Create build-and-push.sh
cat > build-and-push.sh << 'EOF'
#!/bin/bash
set -e

# Load environment variables 
source .env

# Build and push each service
docker build -t $DOCKER_REGISTRY/activity-aggregator:latest -f Dockerfile_activity-aggregator .
docker build -t $DOCKER_REGISTRY/delay-manager:latest -f Dockerfile_delay-manager .
docker build -t $DOCKER_REGISTRY/locations-finder:latest -f Dockerfile_locations-finder .
docker build -t $DOCKER_REGISTRY/mobilization-classifier:latest -f Dockerfile_mobilization-classifier .
docker build -t $DOCKER_REGISTRY/receiver:latest -f Dockerfile_receiver .

docker push $DOCKER_REGISTRY/activity-aggregator:latest
docker push $DOCKER_REGISTRY/delay-manager:latest
docker push $DOCKER_REGISTRY/locations-finder:latest
docker push $DOCKER_REGISTRY/mobilization-classifier:latest
docker push $DOCKER_REGISTRY/receiver:latest

echo "Build and push complete!"
EOF

# Create deploy-app.sh
cat > deploy-app.sh << 'EOF'
#!/bin/bash
set -e

# Load environment variables
source .env
source activity-aggregator-vars.env

# Deploy third-party services first
./deploy-3rdparty.sh

# Deploy each service with environment variable substitution
envsubst < docker-compose-app-activity-aggregator.yml | docker-compose -f - up -d
envsubst < docker-compose-app-delay-manager.yml | docker-compose -f - up -d
envsubst < docker-compose-app-locations-finder.yml | docker-compose -f - up -d
envsubst < docker-compose-app-mobilization-classifier.yml | docker-compose -f - up -d
envsubst < docker-compose-app-web-api.yml | docker-compose -f - up -d

echo "Application deployment complete!"
EOF

# Create deploy-3rdparty.sh
cat > deploy-3rdparty.sh << 'EOF'
#!/bin/bash
set -e

# Deploy third-party services using docker-compose
docker-compose -f docker-compose-3rd-party.yml up -d

echo "Third-party services deployment complete!"
EOF

# Create undeploy-app.sh
cat > undeploy-app.sh << 'EOF'
#!/bin/bash
set -e

# Stop and remove all application containers
docker-compose -f docker-compose-app-activity-aggregator.yml down
docker-compose -f docker-compose-app-delay-manager.yml down
docker-compose -f docker-compose-app-locations-finder.yml down
docker-compose -f docker-compose-app-mobilization-classifier.yml down
docker-compose -f docker-compose-app-web-api.yml down

echo "Application undeployment complete!"
EOF

# Create undeploy-3rdparty.sh
cat > undeploy-3rdparty.sh << 'EOF'
#!/bin/bash
set -e

# Stop and remove all third-party containers
docker-compose -f docker-compose-3rd-party.yml down

echo "Third-party services undeployment complete!"
EOF

# Create aws/deploy-ec2-cloudformation.sh
mkdir -p aws
cat > aws/deploy-ec2-cloudformation.sh << 'EOF'
#!/bin/bash
set -e

# Default values
INSTANCE_TYPE="t2.micro"
STACK_NAME="urban-geo-pulse-stack"

# Required parameters
if [ -z "$1" ] || [ -z "$2" ] || [ -z "$3" ]; then
    echo "Usage: $0 <key-pair-name> <vpc-id> <subnet-id> [instance-type] [stack-name]"
    exit 1
fi

KEY_PAIR_NAME=$1
VPC_ID=$2
SUBNET_ID=$3
INSTANCE_TYPE=${4:-$INSTANCE_TYPE}
STACK_NAME=${5:-$STACK_NAME}

# Create the CloudFormation stack
aws cloudformation create-stack \
    --stack-name "$STACK_NAME" \
    --template-body "file://cloudformation.yaml" \
    --parameters \
        ParameterKey=KeyName,ParameterValue="$KEY_PAIR_NAME" \
        ParameterKey=InstanceType,ParameterValue="$INSTANCE_TYPE" \
        ParameterKey=VpcId,ParameterValue="$VPC_ID" \
        ParameterKey=SubnetId,ParameterValue="$SUBNET_ID"

echo "Waiting for stack creation to complete..."
aws cloudformation wait stack-create-complete --stack-name "$STACK_NAME"

# Get the instance public IP
INSTANCE_IP=$(aws cloudformation describe-stacks \
    --stack-name "$STACK_NAME" \
    --query 'Stacks[0].Outputs[?OutputKey==`PublicIP`].OutputValue' \
    --output text)

echo "EC2 instance created with IP: $INSTANCE_IP"

# Wait for instance to be ready
echo "Waiting for instance to be ready..."
sleep 60

# Create deployment directory on EC2
echo "Creating deployment directory on EC2..."
ssh -o StrictHostKeyChecking=no ec2-user@"$INSTANCE_IP" "mkdir -p ~/deployment"

# Copy deployment files
echo "Copying deployment files..."
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
scp -r "$SCRIPT_DIR"/* ec2-user@"$INSTANCE_IP":~/deployment/

# Deploy on EC2
echo "Deploying services..."
ssh ec2-user@"$INSTANCE_IP" "cd ~/deployment && chmod +x *.sh && ./deploy-app.sh"

echo "Deployment complete!"
EOF

# Make all scripts executable
chmod +x *.sh aws/*.sh

echo "All shell scripts have been created with proper Unix line endings and made executable!"
echo "You can now run the scripts directly, for example: ./build-and-push.sh"
