package io.radicalbit.nsdb.actors

import akka.actor.ActorSystem
import akka.pattern.ask
import akka.testkit.{ImplicitSender, TestKit, TestProbe}
import akka.util.Timeout
import io.radicalbit.nsdb.common.protocol.Bit
import io.radicalbit.nsdb.index._
import io.radicalbit.nsdb.model.SchemaField
import io.radicalbit.nsdb.protocol.MessageProtocol.Commands._
import io.radicalbit.nsdb.protocol.MessageProtocol.Events._
import org.scalatest._

import scala.concurrent.Await
import scala.concurrent.duration._

class SchemaActorSpec
    extends TestKit(ActorSystem("SchemaActorSpec"))
    with ImplicitSender
    with FlatSpecLike
    with Matchers
    with OneInstancePerTest
    with BeforeAndAfter {

  val probe       = TestProbe()
  val schemaActor = system.actorOf(SchemaActor.props("target/test_index_schema_actor", "db", "namespace"))

  before {
    implicit val timeout = Timeout(3 seconds)
    Await.result(schemaActor ? DeleteSchema("db", "namespace", "people"), 3 seconds)
    Await.result(
      schemaActor ? UpdateSchema("db", "namespace", "people", Schema("people", Seq(SchemaField("name", VARCHAR())))),
      3 seconds)
  }

  "SchemaActor" should "get schemas" in {

    probe.send(schemaActor, GetSchema("db", "namespace", "nonexisting"))

    val nonexistingGot = probe.expectMsgType[SchemaGot]
    nonexistingGot.metric shouldBe "nonexisting"
    nonexistingGot.schema shouldBe None

    probe.send(schemaActor, GetSchema("db", "namespace", "people"))

    val existingGot = probe.expectMsgType[SchemaGot]
    existingGot.metric shouldBe "people"
    existingGot.schema shouldBe Some(Schema("people", Seq(SchemaField("name", VARCHAR()))))
  }

  "SchemaActor" should "update schemas in case of success" in {
    probe.send(schemaActor,
               UpdateSchema("db", "namespace", "people", Schema("people", Seq(SchemaField("surname", VARCHAR())))))

    probe.expectMsgType[SchemaUpdated]

    probe.send(schemaActor, GetSchema("db", "namespace", "people"))

    val existingGot = probe.expectMsgType[SchemaGot]
    existingGot.metric shouldBe "people"
    existingGot.schema shouldBe Some(
      Schema("people", Seq(SchemaField("name", VARCHAR()), SchemaField("surname", VARCHAR())))
    )
  }

  "SchemaActor" should "not update schemas in case of failure" in {
    probe.send(schemaActor,
               UpdateSchema("db", "namespace", "people", Schema("people", Seq(SchemaField("name", BOOLEAN())))))

    val failed = probe.expectMsgType[UpdateSchemaFailed]
    failed.errors shouldBe List("")

    probe.send(schemaActor, GetSchema("db", "namespace", "people"))

    val existingGot = probe.expectMsgType[SchemaGot]
    existingGot.metric shouldBe "people"
    existingGot.schema shouldBe Some(
      Schema("people", Seq(SchemaField("name", VARCHAR())))
    )
  }

  "SchemaActor" should "update schemas coming from a record" in {
    probe.send(
      schemaActor,
      UpdateSchemaFromRecord("db", "namespace", "people", Bit(0, 23.5, Map("name" -> "john", "surname" -> "doe"))))

    probe.expectMsgType[SchemaUpdated]

    probe.send(schemaActor, GetSchema("db", "namespace", "people"))

    val existingGot = probe.expectMsgType[SchemaGot]
    existingGot.metric shouldBe "people"
    existingGot.schema shouldBe Some(
      Schema("people",
             Seq(SchemaField("name", VARCHAR()), SchemaField("surname", VARCHAR()), SchemaField("value", DECIMAL())))
    )

    probe.send(schemaActor, UpdateSchemaFromRecord("db", "namespace", "noDimensions", Bit(0, 23.5, Map.empty)))

    probe.expectMsgType[SchemaUpdated]
  }

  "SchemaActor" should "return the same schema for a new schema included in the old one" in {
    probe.send(
      schemaActor,
      UpdateSchemaFromRecord("db", "namespace", "people", Bit(0, 23, Map("name" -> "john", "surname" -> "doe"))))

    probe.expectMsgType[SchemaUpdated]

    probe.send(schemaActor, GetSchema("db", "namespace", "people"))

    val existingGot = probe.expectMsgType[SchemaGot]
    existingGot.metric shouldBe "people"
    existingGot.schema shouldBe Some(
      Schema("people",
             Seq(SchemaField("name", VARCHAR()), SchemaField("surname", VARCHAR()), SchemaField("value", INT())))
    )

    probe.send(schemaActor, UpdateSchemaFromRecord("db", "namespace", "people", Bit(0, 2, Map("name" -> "john"))))
    probe.expectMsgType[SchemaUpdated]

    probe.send(schemaActor, GetSchema("db", "namespace", "people"))

    val newGot = probe.expectMsgType[SchemaGot]
    newGot.metric shouldBe "people"
    newGot.schema shouldBe existingGot.schema
  }
}
