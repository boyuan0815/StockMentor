pipeline {
    agent { label 'linux' }

    options {
        timestamps()
        disableConcurrentBuilds()
    }

    parameters {
        string(name: 'DOCKERHUB_NAMESPACE', defaultValue: 'your-dockerhub-namespace', description: 'Docker Hub namespace for StockMentor images.')
        string(name: 'JMETER_TARGET_HOST', defaultValue: 'localhost', description: 'Use localhost when Jenkins runs on the Docker host. Use backend only when JMeter runs inside the Compose network.')
        string(name: 'JMETER_TARGET_PORT', defaultValue: '8080', description: 'Backend port reachable from the Jenkins agent.')
        string(name: 'EXPO_PUBLIC_API_BASE_URL', defaultValue: 'http://localhost:8080', description: 'API base URL baked into the Expo Web image.')
    }

    environment {
        COMPOSE_PROJECT_NAME = 'stockmentor'
        BACKEND_IMAGE_NAME = 'stockmentor-backend'
        WEB_IMAGE_NAME = 'stockmentor-web'
        JMETER_PLAN = 'tests/performance/stockmentor-health.jmx'
    }

    stages {
        stage('Checkout') {
            steps {
                checkout scm
                script {
                    env.SHORT_SHA = sh(script: 'git rev-parse --short=12 HEAD', returnStdout: true).trim()
                }
                sh 'git status --short'
                sh '''
                    set -eu
                    case "${DOCKERHUB_NAMESPACE}" in
                        your-dockerhub-namespace|your-dockerhub-username|"")
                            echo "Set DOCKERHUB_NAMESPACE to your real Docker Hub namespace before running this pipeline."
                            exit 1
                            ;;
                    esac
                '''
            }
        }

        stage('Backend build/test') {
            steps {
                sh '''
                    set -eu
                    cd backend
                    chmod +x ./mvnw || true
                    ./mvnw -B clean test
                '''
            }
        }

        stage('Frontend lint/typecheck/export') {
            steps {
                sh '''
                    set -eu
                    cd frontend
                    npm ci
                    npm run lint
                    npx tsc --noEmit
                    EXPO_PUBLIC_API_BASE_URL="${EXPO_PUBLIC_API_BASE_URL}" npx expo export --platform web
                '''
            }
        }

        stage('Docker build backend/frontend') {
            steps {
                sh '''
                    set -eu
                    docker build \
                        -t "${DOCKERHUB_NAMESPACE}/${BACKEND_IMAGE_NAME}:latest" \
                        -t "${DOCKERHUB_NAMESPACE}/${BACKEND_IMAGE_NAME}:${SHORT_SHA}" \
                        .
                    docker build \
                        --build-arg EXPO_PUBLIC_API_BASE_URL="${EXPO_PUBLIC_API_BASE_URL}" \
                        -t "${DOCKERHUB_NAMESPACE}/${WEB_IMAGE_NAME}:latest" \
                        -t "${DOCKERHUB_NAMESPACE}/${WEB_IMAGE_NAME}:${SHORT_SHA}" \
                        -f frontend/Dockerfile frontend
                '''
            }
        }

        stage('Docker Compose up mysql/backend/frontend') {
            steps {
                withCredentials([file(credentialsId: 'stockmentor-compose-env', variable: 'COMPOSE_ENV_FILE')]) {
                    sh '''
                        set -eu
                        docker compose -p "${COMPOSE_PROJECT_NAME}" --env-file "${COMPOSE_ENV_FILE}" up -d --force-recreate mysql backend frontend
                        docker compose -p "${COMPOSE_PROJECT_NAME}" --env-file "${COMPOSE_ENV_FILE}" ps
                    '''
                }
            }
        }

        stage('Wait for /api/health and /api/health/database') {
            steps {
                sh '''
                    set -eu

                    wait_for_up() {
                        path="$1"
                        start_time="$(date +%s)"
                        timeout_seconds=120

                        while [ $(( $(date +%s) - start_time )) -lt "${timeout_seconds}" ]; do
                            body="$(curl -fsS --max-time 5 "http://${JMETER_TARGET_HOST}:${JMETER_TARGET_PORT}${path}" 2>/dev/null || true)"

                            if printf '%s' "${body}" | grep -q '"status"' && printf '%s' "${body}" | grep -q '"UP"'; then
                                echo "${path} is UP"
                                return 0
                            fi

                            echo "Waiting for ${path}..."
                            sleep 5
                        done

                        echo "Timed out waiting for ${path}"
                        docker compose -p "${COMPOSE_PROJECT_NAME}" ps || true
                        docker compose -p "${COMPOSE_PROJECT_NAME}" logs --tail=100 backend || true
                        return 1
                    }

                    wait_for_up /api/health
                    wait_for_up /api/health/database
                '''
            }
        }

        stage('JMeter performance test') {
            steps {
                sh '''
                    set -eu
                    RESULT_DIR="build/jmeter-${BUILD_NUMBER}"
                    REPORT_DIR="${RESULT_DIR}/html"
                    RESULT_FILE="${RESULT_DIR}/results.jtl"

                    if [ -e "${REPORT_DIR}" ]; then
                        echo "JMeter report folder already exists; use a clean build number or workspace."
                        exit 1
                    fi

                    mkdir -p "${REPORT_DIR}"
                    jmeter -n \
                        -t "${JMETER_PLAN}" \
                        -JtargetHost="${JMETER_TARGET_HOST}" \
                        -JtargetPort="${JMETER_TARGET_PORT}" \
                        -JconnectTimeout=5000 \
                        -JresponseTimeout=10000 \
                        -l "${RESULT_FILE}" \
                        -e -o "${REPORT_DIR}"
                '''
            }
        }

        stage('Docker push latest and commit SHA tags') {
            steps {
                withCredentials([usernamePassword(credentialsId: 'dockerhub-credentials', usernameVariable: 'DOCKERHUB_USER', passwordVariable: 'DOCKERHUB_TOKEN')]) {
                    sh '''
                        set -eu
                        set +x
                        echo "${DOCKERHUB_TOKEN}" | docker login -u "${DOCKERHUB_USER}" --password-stdin
                        set -x
                        docker push "${DOCKERHUB_NAMESPACE}/${BACKEND_IMAGE_NAME}:latest"
                        docker push "${DOCKERHUB_NAMESPACE}/${BACKEND_IMAGE_NAME}:${SHORT_SHA}"
                        docker push "${DOCKERHUB_NAMESPACE}/${WEB_IMAGE_NAME}:latest"
                        docker push "${DOCKERHUB_NAMESPACE}/${WEB_IMAGE_NAME}:${SHORT_SHA}"
                        docker logout
                    '''
                }
            }
        }

        stage('Archive JMeter reports') {
            steps {
                archiveArtifacts artifacts: 'build/jmeter-*/**', fingerprint: true, allowEmptyArchive: true
            }
        }
    }

    post {
        always {
            archiveArtifacts artifacts: 'build/jmeter-*/**', fingerprint: true, allowEmptyArchive: true
            publishHTML(target: [
                allowMissing: true,
                alwaysLinkToLastBuild: true,
                keepAll: true,
                reportDir: "build/jmeter-${BUILD_NUMBER}/html",
                reportFiles: 'index.html',
                reportName: 'JMeter HTML Report'
            ])
        }
    }
}
