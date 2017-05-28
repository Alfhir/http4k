package worked_example._3_adding_another_endpoint

import com.natpryce.hamkrest.equalTo
import com.natpryce.hamkrest.should.shouldMatch
import org.http4k.client.OkHttp
import org.http4k.core.HttpHandler
import org.http4k.core.Method.GET
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status
import org.http4k.core.Status.Companion.BAD_REQUEST
import org.http4k.core.Status.Companion.OK
import org.http4k.core.then
import org.http4k.filter.ServerFilters.CatchLensFailure
import org.http4k.lens.Query
import org.http4k.lens.int
import org.http4k.routing.by
import org.http4k.routing.routes
import org.http4k.server.Http4kServer
import org.http4k.server.Jetty
import org.http4k.server.asServer
import org.junit.After
import org.junit.Before
import org.junit.Test
import worked_example._3_adding_another_endpoint.Matchers.answerShouldBe
import worked_example._3_adding_another_endpoint.Matchers.statusShouldBe

/**
 * 3. Refactoring towards another business story.
 *
 * REQUIREMENTS:
 * - Implement a "multiply" service, which will find the product of a number of integer values.
 */

/** TESTS **/
object Matchers {
    fun Response.statusShouldBe(expected: Status) = status shouldMatch equalTo(expected)

    fun Response.answerShouldBe(expected: Int) {
        statusShouldBe(OK)
        bodyString().toInt() shouldMatch equalTo(expected)
    }
}

class EndToEndTest {
    private val port = 8000
    private val client = OkHttp()
    private val server = MyMathServer(port)

    @Before
    fun setup(): Unit {
        server.start()
    }

    @After
    fun teardown(): Unit {
        server.stop()
    }

    @Test
    fun `all endpoints are mounted correctly`() {
        client(Request(GET, "http://localhost:$port/ping")).statusShouldBe(OK)
        client(Request(GET, "http://localhost:$port/add?value=1&value=2")).answerShouldBe(3)
        client(Request(GET, "http://localhost:$port/multiply?value=2&value=4")).answerShouldBe(8)
    }
}

class AddFunctionalTest {
    private val client = MyMathsApp()

    @Test
    fun `adds values together`() {
        client(Request(GET, "/add?value=1&value=2")).answerShouldBe(3)
    }

    @Test
    fun `answer is zero when no values`() {
        client(Request(GET, "/add")).answerShouldBe(0)
    }

    @Test
    fun `bad request when some values are not numbers`() {
        client(Request(GET, "/add?value=1&value=notANumber")).statusShouldBe(BAD_REQUEST)
    }
}

class MultiplyFunctionalTest {
    private val client = MyMathsApp()

    @Test
    fun `products values together`() {
        client(Request(GET, "/multiply?value=2&value=4")).answerShouldBe(8)
    }

    @Test
    fun `answer is zero when no values`() {
        client(Request(GET, "/multiply")).answerShouldBe(0)
    }

    @Test
    fun `bad request when some values are not numbers`() {
        client(Request(GET, "/multiply?value=1&value=notANumber")).statusShouldBe(BAD_REQUEST)
    }
}

/** PRODUCTION **/
fun MyMathServer(port: Int): Http4kServer = MyMathsApp().asServer(Jetty(port))

fun MyMathsApp(): HttpHandler = CatchLensFailure.then(
    routes(
        GET to "/ping" by { _: Request -> Response(OK) },
        GET to "/add" by calculate { it.sum() },
        GET to "/multiply" by calculate { it.fold(1) { memo, next -> memo * next } }
    )
)

private fun calculate(fn: (List<Int>) -> Int): (Request) -> Response {
    val values = Query.int().multi.defaulted("value", listOf())

    return {
        request: Request ->
        val valuesToCalc = values.extract(request)
        val answer = if (valuesToCalc.isEmpty()) 0 else fn(valuesToCalc)
        Response(OK).body(answer.toString())
    }
}
