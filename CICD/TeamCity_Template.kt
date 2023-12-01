package _self.buildTypes
import jetbrains.buildServer.configs.kotlin.v2019_2.*
import jetbrains.buildServer.configs.kotlin.v2019_2.buildFeatures.dockerSupport
import jetbrains.buildServer.configs.kotlin.v2019_2.buildSteps.GradleBuildStep
import jetbrains.buildServer.configs.kotlin.v2019_2.buildSteps.dockerCommand
import jetbrains.buildServer.configs.kotlin.v2019_2.buildSteps.gradle
import jetbrains.buildServer.configs.kotlin.v2019_2.buildSteps.script
import jetbrains.buildServer.configs.kotlin.v2019_2.triggers.finishBuildTrigger
import jetbrains.buildServer.configs.kotlin.v2019_2.buildSteps.python
import jetbrains.buildServer.configs.kotlin.v2019_2.triggers.vcs
import jetbrains.buildServer.configs.kotlin.v2019_2.buildFeatures.commitStatusPublisher
import jetbrains.buildServer.configs.kotlin.v2019_2.buildFeatures.vcsLabeling

object DockerBuildTemplateJdk17OpenJ9New : Template({
    id("docker_build_template_jdk17_openj9_new")
    name = "GradleDockerBuildTemplateJdk17OpenJ9New"

    params {
        param("fullVersion", "")
        param("nexus_repo_url_docker_hosted", "nexusurl/%nexus_repo_docker_hosted%")
        param("service_image", "%nexus_repo_url_docker_hosted%/%service_name%:%service_version%")
        param("nexus_repo_url_mvn_hosted", "https://nexusurl/repository/%nexus_repo_mvn_hosted%/")
        param("service_version", "%fullVersion%")
        param("vcsroot.url", "")
        param("env.ProjectID", "%system.teamcity.buildType.id%")
        param("gradle_params","-Pversion=%service_version% -x :api:publish -x :checkstyleMain -x :checkstyleTest --refresh-dependencies -Dgradle.wrapperUser=%nexus_login% -Dgradle.wrapperPassword=%nexus_password% -Dgradle.apiLibRepository=%nexus_repo_url_mvn_hosted%")
        param("docker_file","")
    }

    steps {

        gradle {
            name = "Compile application"
            tasks = "clean build"
            buildFile = "build.gradle"
            gradleParams = "%gradle_params%"
            jvmArgs = "-Xms256m -Xmx4096m"
            dockerImagePlatform = GradleBuildStep.ImagePlatform.Linux
            dockerImage = "nexusurl/repo/eclipse-temurin:17-alpine"
        }

        gradle {
            name = "Code quality check"
            tasks = "sonarqube"
            gradleParams = """
                %gradle_params%
                -Dsonar.host.url=%sonar_url%
                -Dsonar.links.ci=%teamcity.serverUrl%/viewType.html?buildTypeId=%system.teamcity.buildType.id%
                -Dsonar.links.scm=%vcsroot.url%
                -Dsonar.sourceEncoding=UTF-8
                -Dsonar.branch.name=%teamcity.build.branch%
                -Dsonar.login=%sonar_token%
            """.trimIndent()
            jvmArgs = "-Xms256m -Xmx4096m"
            dockerImagePlatform = GradleBuildStep.ImagePlatform.Linux
            dockerImage = "nexusurl/repo/eclipse-temurin:17-alpine"
        }


        dockerCommand {
            name = "Build image"
            conditions {
                endsWith("docker_file", "Dockerfile")
            }
            commandType = build {
                source = file {
                    path = "%docker_file%"
                }
                contextDir = "/"
                namesAndTags = "%service_image%"
                commandArgs = "--rm"
            }
            param("dockerImage.platform", "linux")
        }

        dockerCommand {
            name = "Build image"
            conditions {
                doesNotContain("docker_file", "Dockerfile")
            }
            commandType = build {
                source = content {
                    content = """FROM nexusurl/repo/eclipse-temurin:17-alpine

                    COPY "./build/libs/app.jar" ./app.jar
                    RUN chmod +x app.jar
                    USER 1000
                    ENTRYPOINT exec java %java_opts% -jar app.jar
                    """.trimIndent()
                }
                contextDir = "/"
                namesAndTags = "%service_image%"
                commandArgs = "--rm"
            }
            param("dockerImage.platform", "linux")
            param("java_opts", "")
        }

        script {
            name = "Scan image"
            scriptContent = """
                #!/bin/bash
                
                #   Check twistcli version
                TWISTCLI_VERSION=${'$'}(./twistcli | grep -A1 VERSION | sed 1d)
                CONSOLE_VERSION=${'$'}(curl -k -s -u %prismacloud_login%:%prismacloud_password% %prismacloud_url%/api/v1/version | tr -d \"\')
                
                echo "TWISTCLI_VERSION = ${'$'}TWISTCLI_VERSION"
                echo "CONSOLE_VERSION = ${'$'}CONSOLE_VERSION"
                
                if [[ ${'$'}TWISTCLI_VERSION != ${'$'}CONSOLE_VERSION ]]; then
                    echo "downloading twistcli"
                    curl -k -s -u %prismacloud_login%:%prismacloud_password% --output ./twistcli %prismacloud_url%/api/v1/util/twistcli
                    chmod +x ./twistcli
                    #   Scan image with Twistcli
                    ./twistcli images scan --docker-address %env.DOCKER_HOST% --address %prismacloud_url% -u %prismacloud_login% -p %prismacloud_password% --details %service_image%
                    #   Remove twistcli
                    rm -f ./twistcli
                else
                    #   Scan image with Twistcli
                    twistcli images scan --docker-address %env.DOCKER_HOST% --address %prismacloud_url% -u %prismacloud_login% -p %prismacloud_password% --details %service_image%
                fi
            """.trimIndent()
        }
        step {
            name = "Fortify check"
            type = "FortifyRunner"
            enabled = true
            param("template", "TemplateName")
            param("scancentral", "%fortify.scancentral-client%")
            param("fortify.action2", "inheritance_true")
            param("sources", "%system.teamcity.build.checkoutDir%")
            param("fortify.action", "Async")
            param("versionName", "%service_version%")
            param("applicationName", "%system.teamcity.projectName%")

        }

        script {
            name = "Prepare kubeconfig"
            scriptContent = """
                echo "Kubeconfig path: ${'$'}{KUBECONFIG}"
                mkdir -p "${'$'}(dirname ${'$'}{KUBECONFIG})"
                curl -fk --user '%nexus_login%:%nexus_password%' %nexus_url%/repository/%nexus_raw_repo%/%nexus_subpath%/kube.conf -o %env.KUBECONFIG%.enc -s
                cat %env.KUBECONFIG%.enc | openssl enc -aes-256-cbc -d -pass pass:%password_for_enc% -iter 100000 > %env.KUBECONFIG%
            """.trimIndent()
        }

        script {
            name = "Test Helm Deploy"
            scriptContent = """
            #!/bin/bash
            set -e
			#ENV=dev
            Charts=${'$'}(find %helm_deploy_path% -name "chart")  # Список путей до директорий содержащих папку chart
            for chart in ${'$'}Charts; 
            do 

               values=${'$'}(echo ${'$'}chart | sed "s/chart/env/g")"/${'$'}ENV/values.yaml"
			   
               printf "\nTesting Chart ${'$'}chart with Values file ${'$'}values \n"
			   
               helm lint ${'$'}chart -f ${'$'}values %helm_args%

               helm template ${'$'}chart -f ${'$'}values %helm_args% >> /dev/null
               printf " \nTemplated successfully!\n"

               helm --namespace %helm_deploy_namespace% upgrade %helm_deploy_release_name% ${'$'}chart --install --dry-run --set deploy.image.repository=%nexus_repo_url_docker_hosted%,deploy.image.tag=%service_version% %helm_args% -f ${'$'}values >> /dev/null
               printf " \nDry run is successfull!\n"

            done
            """.trimIndent()
        }

        dockerCommand {
            name = "Push image"
            commandType = push {
                namesAndTags = "%service_image%"
            }
        }

        script {
            name = "Copy artifacts"
            scriptContent = """
                #!/bin/bash
                
                """.trimIndent()
        }
    }

    features {
        dockerSupport {
            id = "DockerSupport"
            loginToRegistry = on {
                dockerRegistryId = "PROJECT_EXT_5"
            }
        }
        feature {
            id = "JetBrains.SonarQube.BranchesAndPullRequests.Support"
            type = "JetBrains.SonarQube.BranchesAndPullRequests.Support"
            param("provider", "GitHub")
        }
        commitStatusPublisher {
            publisher = bitbucketServer {
                url = "%bitbucket_url%"
                userName = "%bitbucket_login%"
                password = "credentialsJSON:3bc01c50-c349-4195-a8ac-ed5a012a02cd"
            }
        }
        vcsLabeling {
            vcsRootId = "__ALL__"
            labelingPattern = "v%service_version%"
            successfulOnly = true
            branchFilter = "+:master"
        }
    }
})