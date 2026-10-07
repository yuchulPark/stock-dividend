pipeline {
    agent any

    options {
        disableConcurrentBuilds()
        timestamps()
    }

    stages {

        stage('Checkout') {
            steps {
                checkout scm
            }
        }

        stage('Backend Test') {
            steps {
                sh '''
                    set -eu

                    CI_NETWORK="stock-ci-${BUILD_NUMBER}"
                    CI_DB="stock-ci-db-${BUILD_NUMBER}"

                    docker network create "$CI_NETWORK" >/dev/null 2>&1 || true
                    docker rm -f "$CI_DB" >/dev/null 2>&1 || true

                    docker run -d \
                      --name "$CI_DB" \
                      --network "$CI_NETWORK" \
                      -e POSTGRES_DB=stock_ci \
                      -e POSTGRES_USER=stock_ci \
                      -e POSTGRES_PASSWORD=stock_ci_pw \
                      postgres:17 >/dev/null

                    for i in $(seq 1 30); do
                        if docker exec "$CI_DB" \
                          pg_isready -U stock_ci -d stock_ci >/dev/null 2>&1; then
                            break
                        fi

                        sleep 2
                    done

                    docker exec "$CI_DB" \
                      pg_isready -U stock_ci -d stock_ci

                    docker run --rm \
                      --network "$CI_NETWORK" \
                      --volumes-from jenkins \
                      -w "$WORKSPACE" \
                      -e TEST_DB_URL="jdbc:postgresql://$CI_DB:5432/stock_ci" \
                      -e TEST_DB_USERNAME=stock_ci \
                      -e TEST_DB_PASSWORD=stock_ci_pw \
                      eclipse-temurin:17-jdk-alpine \
                      sh -c './mvnw -B -ntp test'
                '''
            }
        }

        stage('Frontend Verify') {
            steps {
                sh '''
                    set -eu

                    docker run --rm \
                      --volumes-from jenkins \
                      -w "$WORKSPACE/frontend" \
                      --user "$(id -u):$(id -g)" \
                      -e HOME=/tmp \
                      node:24-alpine \
                      sh -c 'npm ci && npm run lint && npm run build'
                '''
            }
        }

        stage('Deploy') {
            steps {
                sh '''
                    set -eu

                    cd /srv/stock

                    git fetch origin develop
                    git checkout develop
                    git reset --hard origin/develop

                    docker compose config --quiet
                    docker compose up -d --build
                '''
            }
        }

        stage('Verify') {
            steps {
                sh '''
                    set -eu

                    cd /srv/stock

                    docker compose ps

                    for i in $(seq 1 30); do
                        BACKEND_STATUS=$(docker inspect \
                          -f '{{.State.Health.Status}}' \
                          stock-backend 2>/dev/null || true)

                        FRONTEND_STATUS=$(docker inspect \
                          -f '{{.State.Health.Status}}' \
                          stock-frontend 2>/dev/null || true)

                        if [ "$BACKEND_STATUS" = "healthy" ] && \
                           [ "$FRONTEND_STATUS" = "healthy" ]; then
                            break
                        fi

                        echo "Waiting for services..."
                        echo "backend=$BACKEND_STATUS frontend=$FRONTEND_STATUS"

                        sleep 2
                    done

                    test "$(docker inspect -f '{{.State.Health.Status}}' stock-backend)" = "healthy"
                    test "$(docker inspect -f '{{.State.Health.Status}}' stock-frontend)" = "healthy"

                    docker exec stock-backend \
                      wget -q -O - http://127.0.0.1:8080/api/hello

                    echo
                    echo "Deployment verification completed."
                '''
            }
        }
    }

    post {
        always {
            sh '''
                docker rm -f "stock-ci-db-${BUILD_NUMBER}" >/dev/null 2>&1 || true
                docker network rm "stock-ci-${BUILD_NUMBER}" >/dev/null 2>&1 || true
            '''
        }

        success {
            echo 'CI/CD pipeline completed successfully.'
        }

        failure {
            echo 'CI/CD pipeline failed. Check the stage logs.'
        }
    }
}