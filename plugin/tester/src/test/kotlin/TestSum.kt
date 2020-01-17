import io.kotlintest.properties.assertAll
import io.kotlintest.shouldBe
import io.kotlintest.specs.StringSpec

class TestSum : StringSpec({
    "should add two numbers" {
        assertAll { first: Int, second: Int ->
            sum(first, second) shouldBe first + second
        }
    }
})
