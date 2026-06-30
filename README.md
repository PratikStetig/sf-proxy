# Deploy Spring Boot on AWS EC2 using Docker

This guide explains how to deploy a Spring Boot application on an AWS EC2 instance using Docker by cloning the source code from GitHub, building a Docker image, and running the container.

---

## Prerequisites

- AWS EC2 instance (Ubuntu)
- Spring Boot project hosted on GitHub
- Port `5155` open in EC2 Security Group inbound rules

---

## Step 1: Add Dockerfile to Your Project

Create a `Dockerfile` in the root of your project and push it to GitHub along with your source code.

```dockerfile
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY target/*.jar app.jar
EXPOSE 5155
ENTRYPOINT ["java", "-jar", "app.jar"]
```

---

## Step 2: SSH into EC2 and Install Dependencies

Connect to your EC2 instance and install the required tools.

```bash
sudo apt update -y
sudo apt install docker.io git maven -y
sudo systemctl start docker
```

---

## Step 3: Clone Your Repository

```bash
git clone https://github.com/your-username/sobhapxy.git
git@github.com:PratikStetig/sf-proxy.git
cd sobhapxy
```

---

## Step 4: Build the JAR File

```bash
mvn clean package -DskipTests
```

---

## Step 5: Build the Docker Image

```bash
docker build -t sobhapxy .
```

---

## Step 6: Run the Container

```bash
sudo docker run -d -p 5155:5155 --name sobhapxy sobhapxy:latest
```

The application will be accessible at `http://<your-ec2-public-ip>:5155`.

---

## Updating the Application

After pushing new changes to GitHub, run the following commands on the EC2 instance to redeploy:

```bash
git pull
mvn clean package -DskipTests
docker build -t sobhapxy .
docker stop sobhapxy && docker rm sobhapxy
sudo docker run -d -p 5155:5155 --name sobhapxy sobhapxy:latest
```

---

## Common Docker Commands

| Command | Description |
|---------|-------------|
| `docker ps` | List all running containers |
| `docker logs sobhapxy` | View application logs |
| `docker stop sobhapxy` | Stop the running container |
| `docker rm sobhapxy` | Remove the container |
| `docker images` | List all Docker images |

---

## EC2 Security Group Configuration

Ensure the following inbound rules are configured in your EC2 Security Group:

| Port | Protocol | Purpose     |
|------|----------|-------------|
| 22   | TCP      | SSH access  |
| 5155 | TCP      | Application |

---

**Note:** To run Docker commands without `sudo`, execute `sudo usermod -aG docker $USER` and reconnect to the instance.

```bash
# View logs
docker logs sobhapxy

# Follow logs in real time
docker logs -f sobhapxy

# Last 100 lines
docker logs --tail 100 sobhapxy
```