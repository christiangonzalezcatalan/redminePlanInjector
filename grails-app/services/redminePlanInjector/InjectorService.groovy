package redminePlanInjector

import grails.plugins.rest.client.RestBuilder
import grails.transaction.Transactional
import org.grails.web.json.JSONObject
import org.springframework.http.HttpStatus
import grails.util.Holders
import org.bson.types.ObjectId

@Transactional
class InjectorService {
    private static String toolName = 'Redmine'
    RestBuilder restClient = new RestBuilder()
    String redmineUrl = Holders.grailsApplication.config.getProperty('injector.redmineUrl')
    String gemsbbUrl = Holders.grailsApplication.config.getProperty('injector.gemsbbUrl')

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

    private def getMemberByEmail(redmineUserId) {
        def resp = restClient.get(
            "${redmineUrl}/users/${redmineUserId}.json?key=baa9da1d47247ea95bedc425027e7bb30df8f883")

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

    private def getIssuesFromRedmine(redmineProjectId) {
        def resp = restClient.get("${redmineUrl}/issues.json?project_id=${redmineProjectId}")

        if(resp.getStatusCode() != HttpStatus.OK) {
            throw new Exception("Error al obtener los issues de Redmine. HttpStatusCode: ${resp.getStatusCode()}")
        }
        resp.json
    }

    private def saveBlackboardPlan(plan, projectId, taskList) {
        def responsePlan
        if(plan.id == null) {
            responsePlan = restClient.post("${gemsbbUrl}/plans") {
                contentType "application/json"
                json {
                    project = [id: projectId]
                    tasks = taskList
                }
            }
        }
        else {
            responsePlan = restClient.put("${gemsbbUrl}/plans/${plan.id}") {
                contentType "application/json"
                json {
                    id = plan.id
                    project = [id: projectId]
                    tasks = taskList
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
                    //entityType = mapping.entityType
                    //externalId = bbObject.id
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
                    //entityType = mapping.entityType
                    //externalId = bbObject.id
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

    /*
    0. Get proyecto?
    1- Get plan
    2- Get mapping
    3. Get issues
    4. Post/Put plan
    5. Post/Put mapping
    */
    def injectPlan(String projectId, String externalProjectId) {
        def plan = getPlanFromBB(projectId)
        if(plan == null) {
            plan = [
                project: [
                    id: projectId
                ],
                tasks: new LinkedHashMap()
            ]
        }

        def mapping = getMapping(projectId, 'Redmine')
        def redmineIssues = getIssuesFromRedmine(externalProjectId)

        if(redmineIssues.issues.size() > 0) {
            def taskList = []

            redmineIssues.issues.each {
                def issue = it
                def responsibleId = null
                if(issue.assigned_to != null) {
                    responsibleId = getMemberByEmail(issue.assigned_to.id.toInteger()).id
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

            def bbPlan = saveBlackboardPlan(plan, projectId, taskList)
            def bbMapping = saveBlackboardMapping(mapping, projectId, bbPlan)
            println mapping
        }
    }
}
