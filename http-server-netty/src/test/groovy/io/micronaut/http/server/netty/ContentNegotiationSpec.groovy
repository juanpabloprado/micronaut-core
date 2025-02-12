package io.micronaut.http.server.netty

import groovy.transform.InheritConstructors
import io.micronaut.core.type.Argument
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Error
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Post
import io.micronaut.http.annotation.Produces
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.http.hateoas.JsonError
import io.micronaut.http.server.netty.binding.FormDataBindingSpec.FormController.Person
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import reactor.core.publisher.Flux
import spock.lang.Specification
import spock.lang.Unroll

import static io.micronaut.http.server.netty.ContentNegotiationSpec.NegotiatingController.JSON
import static io.micronaut.http.server.netty.ContentNegotiationSpec.NegotiatingController.TEXT
import static io.micronaut.http.server.netty.ContentNegotiationSpec.NegotiatingController.XML

@MicronautTest
class ContentNegotiationSpec extends Specification {

    @Inject
    @Client("/")
    HttpClient client

    @Unroll
    void "test ACCEPT header content negotiation #header"() {
        expect:
        Flux.from(client.retrieve(HttpRequest.GET("/negotiate").accept(header as MediaType[]), String))
                .blockFirst() == response

        where:
        header                                                                            | response
        [MediaType.APPLICATION_GRAPHQL_TYPE]                                              | JSON // should default to the all handler
        [new MediaType("application/json;q=0.5"), new MediaType("application/xml;q=0.9")] | XML
        [MediaType.APPLICATION_JSON_TYPE]                                                 | JSON
        [MediaType.APPLICATION_JSON_TYPE, MediaType.APPLICATION_XML_TYPE]                 | JSON
// Add Micronaut Jackson XML after v4 Migration
//        [MediaType.APPLICATION_XML_TYPE, MediaType.APPLICATION_JSON_TYPE]                 | XML
//        [MediaType.APPLICATION_XML_TYPE]                                                  | XML
        [MediaType.TEXT_PLAIN_TYPE]                                                       | TEXT
        [MediaType.ALL_TYPE]                                                              | JSON

    }

    @Unroll
    void "test send and receive picks the correct content type for #contentType"() {
        given: "No content type is sent"
        def person = new Person(name: "Fred", age: 10)
        def request = HttpRequest.POST('/negotiate/process', person)
        if (contentType != null) {
            request = request.contentType(contentType)
                    .accept(contentType)
        }
        def response = Flux.from(client.exchange(request, String))
                .blockFirst()

        expect: "the correct content type was used"
        response.getContentType().get() == expectedContentType
        response.body() == expectedBody

        where:
        contentType                     | expectedContentType             | expectedBody
        null                            | MediaType.APPLICATION_JSON_TYPE | '{"name":"Fred","age":10}'
        MediaType.APPLICATION_JSON_TYPE | MediaType.APPLICATION_JSON_TYPE | '{"name":"Fred","age":10}'
// Add Micronaut Jackson XML after v4 Migration
//        MediaType.APPLICATION_XML_TYPE  | MediaType.APPLICATION_XML_TYPE  | '<Person><name>Fred</name><age>10</age></Person>'
    }

    void "test send unacceptable type"() {
        when: "An unacceptable type is sent"
        Flux.from(client.retrieve(HttpRequest.GET("/negotiate/other")
                .accept(MediaType.APPLICATION_GRAPHQL), Argument.STRING, JsonError.TYPE))
                .blockFirst()

        then: "An exception is thrown that states the acceptable types"
        def e = thrown(HttpClientResponseException)
        def response = e.response
        response.status() == HttpStatus.NOT_ACCEPTABLE
        response.body().toString().contains("Specified Accept Types [application/graphql] not supported. Supported types: [text/plain]")
    }

    @Unroll
    void 'test error handling for content type #contentType'() {
        given: "No content type is sent"
        def request = HttpRequest.GET('/negotiate/error')
        if (contentType != null) {
            request = request.accept(contentType)
        }
        HttpResponse<String> response = null
        try {
            Flux.from(client.exchange(request, String))
                    .blockFirst()
        } catch (HttpClientResponseException e) {
            response = e.response
        }

        expect: "the correct content type was used"
        response != null
        response.getContentType().get() == expectedContentType
        response.body() == expectedBody

        where:
        contentType                     | expectedContentType             | expectedBody
        null                            | MediaType.APPLICATION_XML_TYPE  | '<bad>Bad things happened</bad>'
        MediaType.APPLICATION_XML_TYPE  | MediaType.APPLICATION_XML_TYPE  | '<bad>Bad things happened</bad>'
        MediaType.APPLICATION_JSON_TYPE | MediaType.APPLICATION_JSON_TYPE | '{"message":"Bad things happened"}'
    }

    @Unroll
    void 'test status handling for content type #contentType'() {
        given: "No content type is sent"
        def request = HttpRequest.GET('/negotiate/status')
        if (contentType != null) {
            request = request.accept(contentType)
        }
        HttpResponse<String> response = null
        try {
            response = Flux.from(client.exchange(request, String))
                    .blockFirst()
        } catch (HttpClientResponseException e) {
            response = e.response
        }

        expect: "the correct content type was used"
        response != null
        response.getContentType().get() == expectedContentType
        response.body() == expectedBody
        response.status() == status

        where:
        contentType                     | status                 | expectedContentType             | expectedBody
        null                            | HttpStatus.BAD_REQUEST | MediaType.APPLICATION_XML_TYPE  | '<bad>not a good request</bad>'
        MediaType.APPLICATION_XML_TYPE  | HttpStatus.BAD_REQUEST | MediaType.APPLICATION_XML_TYPE  | '<bad>not a good request</bad>'
        MediaType.APPLICATION_JSON_TYPE | HttpStatus.BAD_REQUEST | MediaType.APPLICATION_JSON_TYPE | '{"message":"not a good request"}'
    }

    @Unroll
    void 'test produces any accepts #accept'() {
        given:
        def request = HttpRequest.GET('/negotiate/any/foo')
        if (accept != null) {
            request = request.accept(accept)
        }

        when:
        HttpResponse<String> response = client.toBlocking().exchange(request, String)

        then:
        response.getContentType().get() == expectedContentType
        response.body() == expectedBody

        where:
        accept                          | expectedContentType             | expectedBody
        null                            | MediaType.TEXT_PLAIN_TYPE       | TEXT
        MediaType.APPLICATION_XML_TYPE  | MediaType.APPLICATION_XML_TYPE  | XML
        MediaType.APPLICATION_JSON_TYPE | MediaType.APPLICATION_JSON_TYPE | JSON
        MediaType.TEXT_PLAIN_TYPE       | MediaType.TEXT_PLAIN_TYPE       | TEXT
        MediaType.ALL_TYPE              | MediaType.TEXT_PLAIN_TYPE       | TEXT
    }

    @Controller("/negotiate")
    static class NegotiatingController {

        public static final String XML = "<hello>world</hello>"
        public static final String JSON = '{"hello":"world"}'
        public static final String TEXT = 'Hello World'

        @Get("/")
        @Produces(MediaType.APPLICATION_XML)
        String xml() {
            return XML
        }

        @Get("/")
        @Produces([MediaType.APPLICATION_JSON, MediaType.ALL])
        String json() {
            return JSON
        }

        @Get("/")
        @Produces(MediaType.TEXT_PLAIN)
        String text() {
            return TEXT
        }

        @Get("/other")
        @Produces(MediaType.TEXT_PLAIN)
        String other() {
            return TEXT
        }

        @Get("/any/foo")
        @Produces(MediaType.ALL)
        HttpResponse<?> any(HttpRequest<?> req) {
            def accept = req.accept()
            if (accept.contains(MediaType.APPLICATION_JSON_TYPE)) {
                return HttpResponse.ok(JSON).contentType(MediaType.APPLICATION_JSON_TYPE)
            } else if (accept.contains(MediaType.APPLICATION_XML_TYPE)) {
                return HttpResponse.ok(XML).contentType(MediaType.APPLICATION_XML_TYPE)
            } else {
                return HttpResponse.ok(TEXT).contentType(MediaType.TEXT_PLAIN_TYPE)
            }
        }

        @Get("/any/{someVariable}")
        @Produces(MediaType.ALL)
        HttpResponse<?> anyWithVariable() {
            throw new UnsupportedOperationException()
        }

        @Post(value = "/process",
                processes = [MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML])
        Person process(Person person) {
            return person
        }

        @Get(value = '/error', produces = [MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON])
        Person error() {
            throw new MyException("Bad things happened")
        }

        @Get(value = '/status', produces = [MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON])
        HttpResponse<?> status() {
            HttpResponse.badRequest()
        }

        @Error(MyException)
        @Produces(MediaType.APPLICATION_JSON)
        JsonError jsonError(MyException e) {
            new JsonError(e.getMessage())
        }

        @Error(MyException)
        @Produces([MediaType.APPLICATION_XML, MediaType.ALL])
        String xmlError(MyException e) {
            "<bad>${e.message}</bad>"
        }

        @Error(status = HttpStatus.BAD_REQUEST)
        @Produces(MediaType.APPLICATION_JSON)
        HttpResponse<JsonError> jsonBad() {
            HttpResponse.badRequest(new JsonError("not a good request"))
        }

        @Error(status = HttpStatus.BAD_REQUEST)
        @Produces([MediaType.APPLICATION_XML, MediaType.ALL])
        HttpResponse<String> xmlBad() {
            HttpResponse.badRequest("<bad>not a good request</bad>")
        }
    }

    @InheritConstructors
    static class MyException extends RuntimeException {}
}
