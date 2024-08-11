
# Use a base image with JDK and Maven installed
FROM maven:3.8.4-openjdk-17 AS build

# Set the working directory
WORKDIR /app

# Copy the order service code into the container
COPY . .

# Package the application
RUN mvn clean package -DskipTests

# Specify the command to run your order service
CMD ["java", "-jar", "target/catalog-1.0-SNAPSHOT.jar"]
