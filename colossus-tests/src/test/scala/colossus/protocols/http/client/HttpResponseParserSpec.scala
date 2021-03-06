package colossus
package protocols.http

import colossus.service.DecodedResult
import core.DataBuffer

import akka.util.ByteString
import org.scalatest.{WordSpec, MustMatchers}

import parsing.ParseException
import parsing.DataSize._


//NOTICE - all expected headers names must lowercase, otherwise these tests will fail equality testing

class HttpResponseParserSpec extends WordSpec with MustMatchers {

  "HttpResponseParser" must {

    "parse a basic response" in {
      val res = ByteString("HTTP/1.1 200 OK\r\n\r\n")
      val parser = HttpResponseParser.static(res.size.bytes)
      val data = DataBuffer(res)
      parser.parse(data) must equal(Some(HttpResponse(HttpVersion.`1.1`, HttpCodes.OK, Nil, ByteString())))
      data.remaining must equal(0)
    }


    "parse a response with no body" in {

      val res = "HTTP/1.1 200 OK\r\nHost: api.foo.bar:444\r\nAccept: */*\r\nAuthorization: Basic XXX\r\nAccept-Encoding: gzip, deflate\r\n\r\n"
      val parser = HttpResponseParser.static()

      val expected = HttpResponse(HttpVersion.`1.1`,
        HttpCodes.OK,
        List("host"->"api.foo.bar:444", "accept"->"*/*", "authorization"->"Basic XXX", "accept-encoding"->"gzip, deflate"),
        ByteString(""))
      parser.parse(DataBuffer(ByteString(res))).toList must equal(List(expected))

    }

    "parse a response with a body" in {
      val content = "{some : json}"
      val size = content.getBytes.size
      val res = s"HTTP/1.1 200 OK\r\nHost: api.foo.bar:444\r\nAccept: */*\r\nAuthorization: Basic XXX\r\nAccept-Encoding: gzip, deflate\r\nContent-Length: $size\r\n\r\n{some : json}"
      val parser = HttpResponseParser.static()

      val expected = HttpResponse(HttpVersion.`1.1`,
        HttpCodes.OK,
        List("host"->"api.foo.bar:444", "accept"->"*/*", "authorization"->"Basic XXX", "accept-encoding"->"gzip, deflate", "content-length"->size.toString),
        ByteString("{some : json}")
        )
      val data = DataBuffer(ByteString(res))
      parser.parse(data).toList must equal(List(expected))
      data.remaining must equal(0)
    }


    "decode a response that was encoded by colossus with no body" in {
      val sent = HttpResponse(HttpVersion.`1.1`,
        HttpCodes.OK,
        List("host"->"api.foo.bar:444", "accept"->"*/*", "authorization"->"Basic XXX", "accept-encoding"->"gzip, deflate"),
        ByteString(""))

      val expected = DecodedResult.Static(sent.copy(headers = ("content-length"->"0") +: sent.headers))

      val serverProtocol = HttpServerCodec.static
      val clientProtocol = HttpClientCodec.static()

      val encodedResponse = serverProtocol.encode(sent).asInstanceOf[DataBuffer]

      val decodedResponse = clientProtocol.decode(encodedResponse)
      decodedResponse.toList must equal(List(expected))
      encodedResponse.remaining must equal(0)
    }

    "decode a response that was encoded by colossus with a body" in {
      val content = "{some : json}"
      val size = content.getBytes.size

      val sent = HttpResponse(HttpVersion.`1.1`,
        HttpCodes.OK,
        List("host"->"api.foo.bar:444", "accept"->"*/*", "authorization"->"Basic XXX", "accept-encoding"->"gzip, deflate"),
        ByteString("{some : json}"))

      val expected = DecodedResult.Static(sent.copy(headers = ("content-length"->size.toString) +: sent.headers ))

      val serverProtocol = HttpServerCodec.static
      val clientProtocol = HttpClientCodec.static()

      val encodedResponse = serverProtocol.encode(sent).asInstanceOf[DataBuffer]

      val decodedResponse = clientProtocol.decode(encodedResponse)
      decodedResponse.toList must equal(List(expected))
      encodedResponse.remaining must equal(0)
    }

    "accept a response under the size limit" in {
      val res = ByteString("HTTP/1.1 200 OK\r\n\r\n")
      val parser = HttpResponseParser.static(res.size.bytes)
      parser.parse(DataBuffer(res)) must equal(Some(HttpResponse(HttpVersion.`1.1`, HttpCodes.OK, Nil, ByteString())))
    }

    "reject a response over the size limit" in {
      val res = ByteString("HTTP/1.1 200 OK\r\n\r\n")
      val parser = HttpResponseParser.static((res.size - 1).bytes)
      intercept[ParseException] {
        parser.parse(DataBuffer(res))
      }
    }

    "parse a response with chunked transfer encoding" in {
      val res = "HTTP/1.1 200 OK\r\ntransfer-encoding: chunked\r\n\r\n3\r\nfoo\r\ne\r\n123456789abcde\r\n0\r\n\r\n"
      val parser = HttpResponseParser.static()

      val parsed = parser.parse(DataBuffer(ByteString(res))).get
      parsed.data must equal (ByteString("foo123456789abcde"))
    }
      

  }


}
