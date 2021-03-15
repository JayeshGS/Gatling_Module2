package gatling_module2

import scala.concurrent.duration._
import io.gatling.core.Predef._
import io.gatling.http.Predef._
import io.gatling.jdbc.Predef._

import scala.util.Random

class GatlingDemoTool extends Simulation {

	val domain = "demostore.gatling.io"

	val categoryfeeder = csv("categoryData.csv").random
	val productdetailsfeeder = jsonFile("productdetails.json").random
	val logincsv = csv("login.csv").circular

	val rnd = new Random()

	def randomString(length : Int): String = {
		rnd.alphanumeric.filter(_.isLetter).take(length).mkString
	}

	val initSession = exec(flushCookieJar)
		.exec(session => session.set("randonNumber", rnd.nextInt))
		.exec(session => session.set("customerLoggedIn", false))
		.exec(session => session.set("cartTotal", 0.00))
		.exec(addCookie(Cookie("sessionID", randomString(10)).withDomain(domain)))
		.exec {session => println(session); session}


	val httpProtocol = http
		.baseUrl("http://" + domain)

	object GDTPages{
		def homePage = {
			exec(http("Load HomePage")
				.get("/")
				.check(status.is(200))
				.check(regex(" <title>Gatling Demo-Store</title>").exists)
				.check(css("#_csrf", nodeAttribute = "content").saveAs("csrf_var")))
		}

		def aboutUs = {
			exec(http("Load About Us")
				.get("/about-us")
				.check(status.is(200))
				.check(substring("About Us")))
		}
	}

	object Catalog {
		object Category {
			def view =
				feed(categoryfeeder)
				.exec(http("Load Category + ${category_type}")
					.get("/category/${category_slug}")
					.check(status.is(200))
//					.check(css("#CategoryName").is("${category_type}"))
				)
		}

		object Product{
			def view = {
				feed(productdetailsfeeder)
					.exec(http("Load Product Page + ${name}")
						.get("/product/${slug}")
						.check(status.is(200))
						.check(css("#ProductDescription").is("${description}")))
			}

			def add =
				exec(view).
				exec(http("Add to Cart")
					.get("/cart/add/${id}")
					.check(status.is(200))
					.check(substring("items in your cart.")))

					.exec( session => {
						val currentCartTotal = session("cartTotal").as[Double]
						val itemPrice = session("price").as[Double]
						session.set("cartTotal", (currentCartTotal + itemPrice))
					})
		}
	}

	object CustomerLogin{
		def loginPage =
			feed(logincsv)
			.exec(http("Load Login page")
				.get("/login")
				.check(status.is(200))
				.check(substring("Username:")))

			.exec {session => println(session); session}

			.exec(http("Customer Login Action")
				.post("/login")
				.check(status.is(200))
				.formParam("_csrf", "${csrf_var}")
				.formParam("username", "${username}")
				.formParam("password", "${password}")
				.check(status.is(200)))

			.exec(session => session.set("customerLoggedIn", true))
			.exec {session => println(session); session}
	}

	object Checkout{
		def viewCart = {

			doIf(session => !session("customerLoggedIn").as[Boolean]){
				exec(CustomerLogin.loginPage)
			}

			.exec(http("Load Cart Page")
				.get("/cart/view")
				.check(status.is(200))
				.check(css("#grandTotal").is("$$${cartTotal}")))
		}

		def completeCheckout = {
			exec(http("Complete Checkout")
				.get("/cart/checkout")
				.check(status.is(200))
				.check(substring("Thanks for your order! See you soon!")))
		}
	}


	val scn = scenario("GatlingDemoTool")
		.exec(initSession)

		.exec(GDTPages.homePage)
		.pause(2)

		.exec(GDTPages.aboutUs)
		.pause(2)

		.exec(Catalog.Category.view)
		.pause(2)


		.exec(Catalog.Product.add)
		.pause(2)

		.exec(Checkout.viewCart)
		.pause(2)

//		.exec(CustomerLogin.loginPage)
//		.pause(2)

		.exec(Checkout.completeCheckout)

	setUp(scn.inject(atOnceUsers(1))).protocols(httpProtocol)
}