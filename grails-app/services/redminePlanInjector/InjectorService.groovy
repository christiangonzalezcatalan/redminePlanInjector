package redminePlanInjector

import grails.plugins.rest.client.RestBuilder
import grails.transaction.Transactional
import org.grails.web.json.JSONObject
import org.springframework.http.HttpStatus
import grails.util.Holders
import org.bson.types.ObjectId
import com.budjb.rabbitmq.publisher.RabbitMessagePublisher
import org.springframework.beans.factory.annotation.Autowired
import groovy.json.JsonOutput

@Transactional
class InjectorService {
    private static String toolName = 'Redmine'
    private static String processName = 'RedminePlanInjector'
    RestBuilder restClient = new RestBuilder()
    String gemsbbUrl = Holders.grailsApplication.config.getProperty('injector.gemsbbUrl')
    @Autowired
    RabbitMessagePublisher rabbitMessagePublisher

    private def buildTask(JSONObject issue) {
        def responsibleId = null
        if(issue.assigned_to != null) {
            responsibleId = getMemberByEmail(issue.assigned_to.id.toInteger()).id
        }

        [
            name: issue.subject,
            startDate: Date.parse('yyyy-MM-dd', issue.start_date),
            dueDate: Date.parse('yyyy-MM-dd', issue.due_date),
            status: issue.status.name,
            responsible: [id: responsibleId],
            contributors: []
        ]
    }

    private def getProjectFromBB(String projectId) {
        def resp = restClient.get("${gemsbbUrl}/projects/${projectId}")

        if(resp.getStatusCode() != HttpStatus.OK) {
            throw new Exception("Error al obtener el registro del plan del Blackboard. HttpStatusCode: ${resp.getStatusCode()}")
        }

        resp.json
    }

    private def getPlanFromBB(String projectId) {
        def resp = restClient.get("${gemsbbUrl}/plans?projectId=${projectId}")

        if(resp.getStatusCode() != HttpStatus.OK) {
            throw new Exception("Error al obtener el registro del plan del Blackboard. HttpStatusCode: ${resp.getStatusCode()}")
        }

        JSONObject result = resp.json

        if(result.size() == 1 || result.id != null) {
            return result
        }
    }

    private def getTaskIdFromMap(id, map){
        def task = map.find() { it.externalId == id.toString() && it.entityType == 'Task' }

        if(task == null) {
            task = [
                internalId: new ObjectId().toString(),
                externalId: id.toString(),
                entityType: 'Task'
            ]
            map.add(task)
        }
        task.internalId
    }

    private def getMemberByEmailFromRedmine(repository, redmineUserId) {
        def resp = restClient.get(
            "${repository.data.root}/users/${redmineUserId}.json?key=${repository.data.apiKey}")

        if(resp.getStatusCode() != HttpStatus.OK) {
            throw new Exception("Error al obtener el usuario de Redmine. HttpStatusCode: ${resp.getStatusCode()}")
        }

        JSONObject result = resp.json

        if(result.user != null) {
            def memberResp = restClient.get(
                "${gemsbbUrl}/members?email=${result.user.mail}")
            memberResp.json
        }
    }

    private def getMappingFromBB(projectId, tool) {
        def resp = restClient.get(
            "${gemsbbUrl}/projects/${projectId}/mappings?tool=${tool}")

        if(resp.getStatusCode() != HttpStatus.OK) {
            throw new Exception("Error al obtener el mapping del plan. HttpStatusCode: ${resp.getStatusCode()}")
        }

        JSONObject result = resp.json

        if(result.size() == 1 || result.id != null) {
            return result
        }
    }

    private def getMapping(projectId, tool) {
        def mapping = getMappingFromBB(projectId, tool)

        if(mapping == null) {
            mapping = [
                project: [
                    id: projectId
                ],
                tool: tool,
                map: new ArrayList()
            ]
        }
        mapping
    }

    private def getIssuesFromRedmine(repository, redmineProjectId) {
        def resp = restClient.get("${repository.data.root}/issues.json?project_id=${redmineProjectId}")

        if(resp.getStatusCode() != HttpStatus.OK) {
            throw new Exception("Error al obtener los issues de Redmine. HttpStatusCode: ${resp.getStatusCode()}")
        }
        resp.json
    }

    private def saveBlackboardPlan(plan, projectId) {
        def responsePlan
        if(plan.id == null) {
            responsePlan = restClient.post("${gemsbbUrl}/plans") {
                contentType "application/json"
                json {
                    project = [id: projectId]
                    tasks = plan.tasks
                }
            }
        }
        else {
            responsePlan = restClient.put("${gemsbbUrl}/plans/${plan.id}") {
                contentType "application/json"
                json {
                    id = plan.id
                    project = [id: projectId]
                    tasks = plan.tasks
                }
            }
        }
        if (responsePlan.getStatusCode() != HttpStatus.OK &&
            responsePlan.getStatusCode() != HttpStatus.CREATED) {
            throw new Exception("Error al guardar el registro del plan. HttpStatusCode: ${responsePlan.getStatusCode()}")
        }

        responsePlan.json
    }

    private def saveBlackboardMapping(mapping, projectId, bbObject) {
        def responseMapping

        if(mapping.id == null) {
            responseMapping = restClient.post("${gemsbbUrl}/projects/${projectId}/mappings") {
                contentType "application/json"
                json {
                    project = mapping.project
                    tool = mapping.tool
                    map = mapping.map
                }
            }
        }
        else {
            responseMapping = restClient.put("${gemsbbUrl}/projects/${projectId}/mappings/${mapping.id}") {
                contentType "application/json"
                json {
                    id = mapping.id
                    project = mapping.project
                    tool = mapping.tool
                    map = mapping.map
                }
            }
        }

        if (responseMapping.getStatusCode() != HttpStatus.OK &&
            responseMapping.getStatusCode() != HttpStatus.CREATED) {
            throw new Exception("Error al guardar el mapping del plan. HttpStatusCode: ${responseMapping.getStatusCode()}")
        }

        responseMapping.json
    }

    private def getToolsConfigurationFromBB() {
        def resp = restClient.get(
            "${gemsbbUrl}/toolsConfiguration?toolName=${InjectorService.toolName}&processName=${InjectorService.processName}")

        if(resp.getStatusCode() != HttpStatus.OK) {
            throw new Exception("Error al obtener la configuración del proceso ${InjectorService.processName}. HttpStatusCode: ${resp.getStatusCode()}")
        }

        resp.json
    }

    private def getRepositoryFromBB(organizationId) {
        def resp = restClient.get(
            "${gemsbbUrl}/organizations/${organizationId}/repositories?toolName=${InjectorService.toolName}")

        if(resp.getStatusCode() != HttpStatus.OK) {
            throw new Exception("Error al obtener el repositorio. HttpStatusCode: ${resp.getStatusCode()}")
        }

        JSONObject result = resp.json

        if(result.size() == 1 || result.id != null) {
            return result
        }
    }

    def injectProcess() {
        def toolsConfig = getToolsConfigurationFromBB()
        toolsConfig.each() {
            def project = getProjectFromBB(it.project.id)
            def repository = getRepositoryFromBB(project.organization.id)

            injectPlan(it.project.id, it.parameters.projectId, repository)
        }
    }

    /*
    0. Get proyecto?
    1- Get plan
    2- Get mapping
    3. Get issues
    4. Post/Put plan
    5. Post/Put mapping
    */
    def injectPlan(String projectId, Integer externalProjectId, repository) {
        def plan = getPlanFromBB(projectId)
        def oldPlan = JsonOutput.toJson(plan)

        if(plan == null) {
            plan = [
                project: [
                    id: projectId
                ],
                tasks: new LinkedHashMap()
            ]
        }

        def mapping = getMapping(projectId, 'Redmine')
        def redmineIssues = getIssuesFromRedmine(repository, externalProjectId)

        if(redmineIssues.issues.size() > 0) {
            def taskList = []

            redmineIssues.issues.each {
                def issue = it
                def responsibleId = null
                if(issue.assigned_to != null) {
                    responsibleId = getMemberByEmailFromRedmine(repository, issue.assigned_to.id.toInteger()).id
                }

                def taskId = getTaskIdFromMap(issue.id, mapping.map)
                def task = [taskId: taskId]
                task << [
                    name: issue.subject,
                    startDate: Date.parse('yyyy-MM-dd', issue.start_date),
                    dueDate: Date.parse('yyyy-MM-dd', issue.due_date),
                    status: issue.status.name,
                    responsible: [id: responsibleId],
                    contributors: []
                ]
                taskList.add(task)
            }

            plan.tasks = taskList
            def bbPlan = saveBlackboardPlan(plan, projectId)
            def bbMapping = saveBlackboardMapping(mapping, projectId, bbPlan)

            def newPlan = JsonOutput.toJson(bbPlan)
            //println JsonOutput.prettyPrint(oldPlan)
            //println JsonOutput.prettyPrint(newPlan)

            if(oldPlan != newPlan) {
                println "Plan del proyecto ${projectId} cargado."
                rabbitMessagePublisher.send {
                    routingKey = 'Plan.update'
                    exchange = 'testGemsBBExchange'
                    body = projectId
                }
            }
            else {
                println "Plan del proyecto ${projectId} sin cambios."
            }
        }
    }
}
