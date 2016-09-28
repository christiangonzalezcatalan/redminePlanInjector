package redminePlanInjector

import static org.mockserver.integration.ClientAndServer.startClientAndServer
import static org.mockserver.model.HttpRequest.request
import static org.mockserver.model.HttpResponse.response

import static redminePlanInjector.Mocks.BlackboardResponses.getToolConfigurationFromBlackboard
import static redminePlanInjector.Mocks.BlackboardResponses.getProjectFromBlackboard
import static redminePlanInjector.Mocks.BlackboardResponses.getRepositoryFromBlackboard
import static redminePlanInjector.Mocks.BlackboardResponses.getPlanFromBlackboard
import static redminePlanInjector.Mocks.BlackboardResponses.getPlanMappingsFromBlackboard
import static redminePlanInjector.Mocks.BlackboardResponses.getMemberByEmailFromBlackboard
import static redminePlanInjector.Mocks.BlackboardResponses.postPlanToBlackbord

import static redminePlanInjector.Mocks.RedmineResponses.getIssuesFromRedmine
import static redminePlanInjector.Mocks.RedmineResponses.getUserFromRedmine
import static redminePlanInjector.Mocks.RedmineResponses.getIssueFromRedmine

import grails.test.mixin.TestFor
import org.mockserver.integration.ClientAndServer
import org.mockserver.model.Header
import org.mockserver.model.Parameter
import org.mockserver.verify.VerificationTimes
import spock.lang.Specification

/**
 * See the API for {@link grails.test.mixin.services.ServiceUnitTestMixin} for usage instructions
 */
@TestFor(InjectorService)
class InjectorServiceSpec extends Specification {

    protected static ClientAndServer mockServer

    def setupSpec() {
        mockServer = startClientAndServer(8081)
    }

    def cleanupSpec() {
        mockServer.stop()
    }

    def setup() {
    }

    def cleanup() {
        mockServer.reset()
    }

    void 'test inject process'() {
        setup:
        //def projectId = '57cc59368acec62bf2f7d7ed'
        //def redmineProjectId = '3'
        def projectId = '57ccad338acec633f77f862e'
        def redmineProjectId = '2'
        def redmineKey = 'baa9da1d47247ea95bedc425027e7bb30df8f883'

        mockServer.when(
                request('/toolsConfiguration')
                        .withMethod('GET')
                        .withQueryStringParameters(new Parameter('toolName', 'Redmine'))
                        .withQueryStringParameters(new Parameter('processName', 'RedminePlanInjector'))
        ).respond(response(getToolConfigurationFromBlackboard())
                .withStatusCode(200)
                .withHeaders(new Header('Content-Type', 'application/json; charset=utf-8'))
        )
        mockServer.when(
                request('/projects/57ccad338acec633f77f862e')
                        .withMethod('GET')
        ).respond(response(getProjectFromBlackboard())
                .withStatusCode(200)
                .withHeaders(new Header('Content-Type', 'application/json; charset=utf-8'))
        )
        mockServer.when(
                request("/organizations/57e89b278acec6487695a4b5/repositories")
                        .withMethod('GET')
                        .withQueryStringParameters(new Parameter('toolName', 'Redmine'))
        ).respond(response(getRepositoryFromBlackboard())
                .withStatusCode(200)
                .withHeaders(new Header('Content-Type', 'application/json; charset=utf-8'))
        )
        mockServer.when(
                request('/plans')
                        .withMethod('GET')
                        .withQueryStringParameters(new Parameter('projectId', projectId))
        ).respond(response(getPlanFromBlackboard())
                .withStatusCode(200)
                .withHeaders(new Header('Content-Type', 'application/json; charset=utf-8'))
        )
        mockServer.when(
                request("/projects/${projectId}/mappings")
                        .withMethod('GET')
                        .withQueryStringParameters(new Parameter('tool', 'Redmine'))
        ).respond(response(getPlanMappingsFromBlackboard())
                .withStatusCode(200)
                .withHeaders(new Header('Content-Type', 'application/json; charset=utf-8'))
        )
        mockServer.when(
                request('/issues.json')
                        .withMethod('GET')
                        .withQueryStringParameters(new Parameter('project_id', redmineProjectId))
        ).respond(response(getIssuesFromRedmine())
                .withStatusCode(200)
                .withHeaders(new Header('Content-Type', 'application/json; charset=utf-8'))
        )
        mockServer.when(
                request('/users/3.json')
                        .withMethod('GET')
                        .withQueryStringParameters(new Parameter('key', redmineKey))
        ).respond(response(getUserFromRedmine(3))
                .withStatusCode(200)
                .withHeaders(new Header('Content-Type', 'application/json; charset=utf-8'))
        )
        mockServer.when(
                request('/users/4.json')
                        .withMethod('GET')
                        .withQueryStringParameters(new Parameter('key', redmineKey))
        ).respond(response(getUserFromRedmine(4))
                .withStatusCode(200)
                .withHeaders(new Header('Content-Type', 'application/json; charset=utf-8'))
        )
        mockServer.when(
                request('/members')
                        .withMethod('GET')
                        .withQueryStringParameters(new Parameter('email', 'christiangonzalezcatalan@hotmail.com'))
        ).respond(response(getMemberByEmailFromBlackboard("57c3c4858acec662dab6dcf4",
                            "christiangonzalezcatalan@hotmail.com",
                            "Christian González"))
                .withStatusCode(200)
                .withHeaders(new Header('Content-Type', 'application/json; charset=utf-8'))
        )
        mockServer.when(
            request('/members')
                    .withMethod('GET')
                    .withQueryStringParameters(new Parameter('email', 'jperez@miempresita.cl'))
        ).respond(response(getMemberByEmailFromBlackboard("57c3c4838acec662dab6dcf2",
                            "jperez@miempresita.cl",
                            "Juan Pérez"))
                .withStatusCode(200)
                .withHeaders(new Header('Content-Type', 'application/json; charset=utf-8'))
        )
        mockServer.when(
            request('/plans/57cf835f8acec65eba3b579f')
            .withMethod('PUT')
        ).respond(response(postPlanToBlackbord())
                .withStatusCode(200)
                .withHeaders(new Header('Content-Type', 'application/json; charset=utf-8'))
        )
        mockServer.when(
                request("/projects/${projectId}/mappings/57d0c86c8acec66d7306700d")
                        .withMethod('PUT')
        ).respond(response(getPlanMappingsFromBlackboard())
                .withStatusCode(200)
                .withHeaders(new Header('Content-Type', 'application/json; charset=utf-8'))
        )

        expect:
        service.injectProcess()

        /*then:
        mockServer.verify(
                request()
                        .withMethod("PUT")
                        .withPath("/plans/57cf835f8acec65eba3b579f")
                ,
                VerificationTimes.exactly(1)
        )*/
    }
}
