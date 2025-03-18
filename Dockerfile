# Base image
FROM openjdk:17-slim

# Install required dependencies
RUN apt-get update && apt-get install -y \
    wget unzip curl \
    libasound2 libatk-bridge2.0-0 libatk1.0-0 libcups2 \
    libdbus-1-3 libdrm2 libgbm1 libnspr4 libnss3 \
    libx11-xcb1 libxcomposite1 libxcursor1 libxdamage1 \
    libxfixes3 libxi6 libxrandr2 libxrender1 libxss1 \
    libxtst6 libglib2.0-0 xvfb \
    libxkbcommon0 \
    libpango1.0-0 \
    && apt-get clean && rm -rf /var/lib/apt/lists/*

# Set environment variables
ENV CHROME_VERSION=132.0.6834.159
ENV CHROMEDRIVER_VERSION=132.0.6834.159
ENV CHROME_URL=https://storage.googleapis.com/chrome-for-testing-public/${CHROME_VERSION}/linux64/chrome-linux64.zip
ENV CHROMEDRIVER_URL=https://storage.googleapis.com/chrome-for-testing-public/${CHROMEDRIVER_VERSION}/linux64/chromedriver-linux64.zip

# Download and install Chrome
RUN wget -q ${CHROME_URL} -O /tmp/chrome-linux64.zip \
    && unzip /tmp/chrome-linux64.zip -d /opt/ \
    && mv /opt/chrome-linux64 /opt/chrome \
    && ln -s /opt/chrome/chrome /usr/local/bin/google-chrome \
    && rm /tmp/chrome-linux64.zip

# Download and install ChromeDriver
RUN wget -q ${CHROMEDRIVER_URL} -O /tmp/chromedriver-linux64.zip \
    && unzip /tmp/chromedriver-linux64.zip -d /opt/ \
    && mv /opt/chromedriver-linux64/chromedriver /usr/local/bin/ \
    && chmod +x /usr/local/bin/chromedriver \
    && rm /tmp/chromedriver-linux64.zip

# Set working directory
WORKDIR /app

# Copy project files
COPY . /app

# Grant execute permission to Gradle wrapper
RUN chmod +x ./gradlew

# Build the application
RUN ./gradlew shadowJar

# 실행할 JAR 파일을 환경 변수로 설정 (각 크롤러별 JAR 경로를 설정 가능)
ENV JAR_FILE=""

# 무한 루프 실행 (크롤러가 종료되더라도 자동으로 다시 시작)
CMD while true; do \
    if [ -f "/app/build/libs/$JAR_FILE" ]; then \
        echo "Executing $JAR_FILE..."; \
        java -jar /app/build/libs/$JAR_FILE; \
        echo "Restarting in 600 seconds..."; \
        sleep 600; \
    else \
        echo "ERROR: JAR file '/app/build/libs/$JAR_FILE' not found!"; \
        exit 1; \
    fi \
done
