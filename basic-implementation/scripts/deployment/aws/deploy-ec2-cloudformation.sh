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
