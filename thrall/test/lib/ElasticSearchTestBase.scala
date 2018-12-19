package lib

import java.util.UUID

import com.gu.mediaservice.model
import com.gu.mediaservice.model._
import helpers.Fixtures
import org.joda.time.{DateTime, DateTimeZone}
import org.scalatest.{BeforeAndAfterAll, FreeSpec, Matchers}
import org.scalatest.concurrent.{Eventually, ScalaFutures}
import play.api.libs.json.{JsDefined, JsLookupResult, Json}

import scala.concurrent.{Await, Future}
import scala.concurrent.duration.{Duration, MILLISECONDS, SECONDS}
import scala.concurrent.ExecutionContext.Implicits.global

trait ElasticSearchTestBase extends FreeSpec with Matchers with Fixtures with BeforeAndAfterAll with Eventually with ScalaFutures {

  val oneHundredMilliseconds = Duration(100, MILLISECONDS)
  val fiveSeconds = Duration(5, SECONDS)

  def ES: ElasticSearchVersion

  override def beforeAll {
    ES.ensureAliasAssigned()
  }

  "Elasticsearch" - {

    "images" - {

      "indexing" - {
        "can index and retrieve images by id" in {
          val id = UUID.randomUUID().toString
          val image = createImageForSyndication(id = UUID.randomUUID().toString, true, Some(DateTime.now()), None)

          Await.result(Future.sequence(ES.indexImage(id, Json.toJson(image))), fiveSeconds) // TODO why is index past in? Is it different to image.id and if so why?

          eventually(timeout(fiveSeconds), interval(oneHundredMilliseconds))(reloadedImage(id).map(_.id) shouldBe Some(image.id))

          reloadedImage(id).get.id shouldBe image.id
        }
      }

      "deleting" - {
        "can delete image" in {
          val id = UUID.randomUUID().toString
          val image = createImageForSyndication(id = UUID.randomUUID().toString, true, Some(DateTime.now()), None)
          Await.result(Future.sequence(ES.indexImage(id, Json.toJson(image))), fiveSeconds)
          eventually(timeout(fiveSeconds), interval(oneHundredMilliseconds))(reloadedImage(id).map(_.id) shouldBe Some(image.id))

          Await.result(Future.sequence(ES.deleteImage(id)), fiveSeconds)

          reloadedImage(id).map(_.id) shouldBe None
        }

        "failed deletes are indiciated with a failed future" in {
          val id = UUID.randomUUID().toString
          val image = createImageForSyndication(id = UUID.randomUUID().toString, true, Some(DateTime.now()), None)
          Await.result(Future.sequence(ES.indexImage(id, Json.toJson(image))), fiveSeconds)
          eventually(timeout(fiveSeconds), interval(oneHundredMilliseconds))(reloadedImage(id).map(_.id) shouldBe Some(image.id))

          val unknownImage = UUID.randomUUID().toString

          whenReady(ES.deleteImage(unknownImage).head.failed) { ex =>
            ex shouldBe ImageNotDeletable
          }
        }

        "should not delete images with usages" in {
          val id = UUID.randomUUID().toString
          val imageWithUsages = createImageForSyndication(id = UUID.randomUUID().toString, true, Some(DateTime.now()), None).copy(usages = List(usage()))
          Await.result(Future.sequence(ES.indexImage(id, Json.toJson(imageWithUsages))), fiveSeconds)
          eventually(timeout(fiveSeconds), interval(oneHundredMilliseconds))(reloadedImage(id).map(_.id) shouldBe Some(imageWithUsages.id))

          whenReady(ES.deleteImage(id).head.failed) { ex =>
            ex shouldBe ImageNotDeletable
          }
        }

        "should not delete images with exports" in {
          val id = UUID.randomUUID().toString
          val imageWithExports = createImageForSyndication(id = UUID.randomUUID().toString, true, Some(DateTime.now()), None).copy(exports = List(crop))
          Await.result(Future.sequence(ES.indexImage(id, Json.toJson(imageWithExports))), fiveSeconds)
          eventually(timeout(fiveSeconds), interval(oneHundredMilliseconds))(reloadedImage(id).map(_.id) shouldBe Some(imageWithExports.id))

          whenReady(ES.deleteImage(id).head.failed) { ex =>
            ex shouldBe ImageNotDeletable
          }
        }

      }
    }

    "collections" - {

      "can set image collections" in {
        val id = UUID.randomUUID().toString
        val imageWithExports = createImageForSyndication(id = UUID.randomUUID().toString, true, Some(DateTime.now()), None).copy(exports = List(crop))
        Await.result(Future.sequence(ES.indexImage(id, Json.toJson(imageWithExports))), fiveSeconds)

        val collection = Collection(path = List("/somewhere"), actionData = ActionData("Test author", DateTime.now), "A test collection")
        val anotherCollection = Collection(path = List("/somewhere-else"), actionData = ActionData("Test author", DateTime.now), "Another test collection")

        val collections = List(collection, anotherCollection)

        Await.result(Future.sequence(ES.setImageCollection(id, JsDefined(Json.toJson(collections)))), fiveSeconds)

        reloadedImage(id).get.collections.size shouldBe 2
        reloadedImage(id).get.collections.head.description shouldEqual "A test collection"
      }
    }

    "exports" - {

      "can add exports" in {
        val id = UUID.randomUUID().toString
        val image = createImageForSyndication(id = UUID.randomUUID().toString, true, Some(DateTime.now()), None)
        Await.result(Future.sequence(ES.indexImage(id, Json.toJson(image))), fiveSeconds)
        reloadedImage(id).get.exports.isEmpty shouldBe true
        val exports = List(crop)

        Await.result(Future.sequence(ES.updateImageExports(id, JsDefined(Json.toJson(exports)))), fiveSeconds)  // TODO rename to add

        reloadedImage(id).get.exports.nonEmpty shouldBe true
        reloadedImage(id).get.exports.head.id shouldBe crop.id
      }

      "can delete exports" in {
        val id = UUID.randomUUID().toString
        val imageWithExports = createImageForSyndication(id = UUID.randomUUID().toString, true, Some(DateTime.now()), None).copy(exports = List(crop))
        Await.result(Future.sequence(ES.indexImage(id, Json.toJson(imageWithExports))), fiveSeconds)
        reloadedImage(id).get.exports.nonEmpty shouldBe true

        Await.result(Future.sequence(ES.deleteImageExports(id)), fiveSeconds)

        reloadedImage(id).get.exports.isEmpty shouldBe true
      }

    }

    "leases" - {

      "can add image lease" in {
        val id = UUID.randomUUID().toString
        val image = createImageForSyndication(id = UUID.randomUUID().toString, true, Some(DateTime.now()), None)
        Await.result(Future.sequence(ES.indexImage(id, Json.toJson(image))), fiveSeconds)
        reloadedImage(id).get.leases.leases.isEmpty shouldBe true

        val lease = model.MediaLease(id = Some(UUID.randomUUID().toString), leasedBy = None, notes = Some("A test lease"), mediaId = UUID.randomUUID().toString)

        Await.result(Future.sequence(ES.addImageLease(id, JsDefined(Json.toJson(lease)), asJsLookup(DateTime.now))), fiveSeconds)

        reloadedImage(id).get.leases.leases.nonEmpty shouldBe true
        reloadedImage(id).get.leases.leases.head.id shouldBe lease.id
      }

      "can remove image lease" in {
        val lease = model.MediaLease(id = Some(UUID.randomUUID().toString), leasedBy = None, notes = Some("A test lease"), mediaId = UUID.randomUUID().toString)
        val id = UUID.randomUUID().toString
        val image = createImageForSyndication(id = UUID.randomUUID().toString, true, Some(DateTime.now()), lease = Some(lease))
        Await.result(Future.sequence(ES.indexImage(id, Json.toJson(image))), fiveSeconds)
        reloadedImage(id).get.leases.leases.nonEmpty shouldBe true

        Await.result(Future.sequence(ES.removeImageLease(id, JsDefined(Json.toJson(lease.id)), asJsLookup(DateTime.now))), fiveSeconds)

        reloadedImage(id).get.leases.leases.isEmpty shouldBe true
      }

      "can replace leases" in {
        val lease = MediaLease(id = Some(UUID.randomUUID().toString), leasedBy = None, notes = Some("A test lease"), mediaId = UUID.randomUUID().toString)
        val id = UUID.randomUUID().toString
        val image = createImageForSyndication(id = UUID.randomUUID().toString, true, Some(DateTime.now()), lease = Some(lease))
        Await.result(Future.sequence(ES.indexImage(id, Json.toJson(image))), fiveSeconds)

        val updatedLease = MediaLease(id = Some(UUID.randomUUID().toString), leasedBy = None, notes = Some("An updated lease"), mediaId = UUID.randomUUID().toString)
        val anotherUpdatedLease = MediaLease(id = Some(UUID.randomUUID().toString), leasedBy = None, notes = Some("Another updated lease"), mediaId = UUID.randomUUID().toString)
        val updatedLeases = LeasesByMedia.build(leases = List(updatedLease, anotherUpdatedLease))
        updatedLeases.leases.size shouldBe 2

        Await.result(Future.sequence(ES.updateImageLeases(id, JsDefined(Json.toJson(updatedLeases)), asJsLookup(DateTime.now))), fiveSeconds)

        reloadedImage(id).get.leases.leases.size shouldBe 2
        reloadedImage(id).get.leases.leases.head.notes shouldBe Some("An updated lease")
      }
    }

    "image usages" - {

      "can delete all usages for an image" in {
        val id = UUID.randomUUID().toString
        val imageWithUsages = createImageForSyndication(id = UUID.randomUUID().toString, true, Some(DateTime.now()), None).copy(usages = List(usage()))
        Await.result(Future.sequence(ES.indexImage(id, Json.toJson(imageWithUsages))), fiveSeconds)

        Await.result(Future.sequence(ES.deleteAllImageUsages(id)), fiveSeconds)

        eventually(timeout(fiveSeconds), interval(oneHundredMilliseconds))(reloadedImage(id).get.usages.isEmpty shouldBe true)
      }

      "can update usages" in {
        val id = UUID.randomUUID().toString
        val image = createImageForSyndication(id = UUID.randomUUID().toString, true, Some(DateTime.now()), None)
        Await.result(Future.sequence(ES.indexImage(id, Json.toJson(image))), fiveSeconds)

        Await.result(Future.sequence(ES.updateImageUsages(id, JsDefined(Json.toJson(List(usage()))), asJsLookup(DateTime.now))), fiveSeconds)

        reloadedImage(id).get.usages.size shouldBe 1
      }

      "should ignore usage update requests when the proposed last modified date is older than the current" in {
        val id = UUID.randomUUID().toString
        val image = createImageForSyndication(id = UUID.randomUUID().toString, true, Some(DateTime.now()), None)
        Await.result(Future.sequence(ES.indexImage(id, Json.toJson(image))), fiveSeconds)

        val mostRecentUsage = usage(id = "recent")
        Await.result(Future.sequence(ES.updateImageUsages(id, JsDefined(Json.toJson(List(mostRecentUsage))), asJsLookup(DateTime.now))), fiveSeconds)

        val staleUsage = usage(id = "stale")
        val staleLastModified = DateTime.now.minusWeeks(1)
        Await.result(Future.sequence(ES.updateImageUsages(id, JsDefined(Json.toJson(List(staleUsage))), asJsLookup(staleLastModified))), fiveSeconds)

        reloadedImage(id).get.usages.head.id shouldEqual("recent")
      }
    }

    "syndication rights" - {
      "updated syndication rights should be persisted" in {
        val id = UUID.randomUUID().toString
        val image = createImageForSyndication(id = UUID.randomUUID().toString, true, Some(DateTime.now()), None)
        ES.indexImage(id, Json.toJson(image))
        eventually(timeout(fiveSeconds), interval(oneHundredMilliseconds))(reloadedImage(id).map(_.id) shouldBe Some(image.id))

        val newSyndicationRights = SyndicationRights(published = Some(DateTime.now()), suppliers = Seq.empty, rights = Seq.empty)

        Await.result(Future.sequence(ES.updateImageSyndicationRights(id, Some(newSyndicationRights))), fiveSeconds)

        reloadedImage(id).flatMap(_.syndicationRights) shouldEqual Some(newSyndicationRights)
      }

      "updating syndication rights should update last modified date" in {
        val id = UUID.randomUUID().toString
        val image = createImageForSyndication(id = UUID.randomUUID().toString, true, Some(DateTime.now()), None)
        ES.indexImage(id, Json.toJson(image))
        eventually(timeout(fiveSeconds), interval(oneHundredMilliseconds))(reloadedImage(id).map(_.id) shouldBe Some(image.id))

        val newSyndicationRights = SyndicationRights(published = Some(DateTime.now().minusWeeks(1)), suppliers = Seq.empty, rights = Seq.empty)
        val beforeUpdate = DateTime.now()

        Await.result(Future.sequence(ES.updateImageSyndicationRights(id, Some(newSyndicationRights))), fiveSeconds)

        reloadedImage(id).get.lastModified.get.isAfter(beforeUpdate) shouldEqual true
      }

      "can delete syndication rights" in {
        val id = UUID.randomUUID().toString
        val image = createImageForSyndication(id = UUID.randomUUID().toString, true, Some(DateTime.now()), None)
        ES.indexImage(id, Json.toJson(image))
        eventually(timeout(fiveSeconds), interval(oneHundredMilliseconds))(reloadedImage(id).map(_.id) shouldBe Some(image.id))
        reloadedImage(id).get.syndicationRights.nonEmpty shouldBe true

        Await.result(Future.sequence(ES.deleteSyndicationRights(id)), fiveSeconds)

        reloadedImage(id).get.syndicationRights.isEmpty shouldBe true
      }
    }

    "user metadata" - {
      "can update user metadata for an existing image" in {
        val id = UUID.randomUUID().toString
        val imageWithBoringMetadata = createImageForSyndication(id = UUID.randomUUID().toString, true, Some(DateTime.now()), None)

        ES.indexImage(id, Json.toJson(imageWithBoringMetadata))
        eventually(timeout(fiveSeconds), interval(oneHundredMilliseconds))(reloadedImage(id).map(_.id) shouldBe Some(imageWithBoringMetadata.id))

        val updatedMetadata = Some(Edits(metadata = imageWithBoringMetadata.metadata.copy(description = Some("An interesting image"))))
        val updatedLastModifiedDate = DateTime.now

        Await.result(Future.sequence(
          ES.applyImageMetadataOverride(id,
            JsDefined(Json.toJson(updatedMetadata)),
            asJsLookup(updatedLastModifiedDate))),
          fiveSeconds)

        reloadedImage(id).flatMap(_.userMetadata.get.metadata.description) shouldBe Some("An interesting image")
      }

      "updating user metadata should update the image and user meta data last modified dates" in {
        val id = UUID.randomUUID().toString
        val imageWithBoringMetadata = createImageForSyndication(id = UUID.randomUUID().toString, true, Some(DateTime.now()), None)

        ES.indexImage(id, Json.toJson(imageWithBoringMetadata))
        eventually(timeout(fiveSeconds), interval(oneHundredMilliseconds))(reloadedImage(id).map(_.id) shouldBe Some(imageWithBoringMetadata.id))

        val updatedMetadata = Some(Edits(metadata = imageWithBoringMetadata.metadata.copy(description = Some("An updated image"))))
        val updatedLastModifiedDate = DateTime.now.withZone(DateTimeZone.UTC)

        Await.result(Future.sequence(
          ES.applyImageMetadataOverride(id,
            JsDefined(Json.toJson(updatedMetadata)),
            asJsLookup(updatedLastModifiedDate))),
          fiveSeconds)

        reloadedImage(id).flatMap(_.userMetadataLastModified) shouldEqual Some(updatedLastModifiedDate)
        reloadedImage(id).flatMap(_.lastModified) shouldEqual Some(updatedLastModifiedDate)
      }

      "original metadata is unchanged by a user metadata edit" in {
        val id = UUID.randomUUID().toString
        val imageWithBoringMetadata = createImageForSyndication(id = UUID.randomUUID().toString, true, Some(DateTime.now()), None)

        ES.indexImage(id, Json.toJson(imageWithBoringMetadata))
        eventually(timeout(fiveSeconds), interval(oneHundredMilliseconds))(reloadedImage(id).map(_.id) shouldBe Some(imageWithBoringMetadata.id))

        val updatedMetadata = Some(Edits(metadata = imageWithBoringMetadata.metadata.copy(description = Some("An interesting image"))))
        val updatedLastModifiedDate = DateTime.now

        Await.result(Future.sequence(
          ES.applyImageMetadataOverride(id,
            JsDefined(Json.toJson(updatedMetadata)),
            asJsLookup(updatedLastModifiedDate))),
          fiveSeconds)

        reloadedImage(id).map(_.originalMetadata) shouldEqual Some(imageWithBoringMetadata.originalMetadata)
      }

      "should ignore update if the proposed modification date is older than the current user metadata last modified date" in {
        val id = UUID.randomUUID().toString
        val imageWithBoringMetadata = createImageForSyndication(id = UUID.randomUUID().toString, true, Some(DateTime.now()), None)

        ES.indexImage(id, Json.toJson(imageWithBoringMetadata))
        eventually(timeout(fiveSeconds), interval(oneHundredMilliseconds))(reloadedImage(id).map(_.id) shouldBe Some(imageWithBoringMetadata.id))

        val latestMetadata = Some(Edits(metadata = imageWithBoringMetadata.metadata.copy(description = Some("Latest edit"))))
        val latestLastModifiedDate = DateTime.now.withZone(DateTimeZone.UTC)

        Await.result(Future.sequence(
          ES.applyImageMetadataOverride(id,
            JsDefined(Json.toJson(latestMetadata)),
            asJsLookup(latestLastModifiedDate))),
          fiveSeconds)

        val staleMetadata = Some(Edits(metadata = imageWithBoringMetadata.metadata.copy(description = Some("A stale edit"))))
        val staleLastModifiedDate = latestLastModifiedDate.minusSeconds(1)

        Await.result(Future.sequence(
          ES.applyImageMetadataOverride(id,
            JsDefined(Json.toJson(staleMetadata)),
            asJsLookup(staleLastModifiedDate))),
          fiveSeconds)

        reloadedImage(id).flatMap(_.userMetadata.get.metadata.description) shouldBe Some("Latest edit")
        reloadedImage(id).flatMap(_.userMetadataLastModified) shouldEqual Some(latestLastModifiedDate)
      }

      "updating user metadata with new usage rights should update usage rights" in {
        val id = UUID.randomUUID().toString
        val imageWithUsageRights = createImageForSyndication(id = UUID.randomUUID().toString, true, Some(DateTime.now()), None)

        ES.indexImage(id, Json.toJson(imageWithUsageRights))
        eventually(timeout(fiveSeconds), interval(oneHundredMilliseconds))(reloadedImage(id).map(_.id) shouldBe Some(imageWithUsageRights.id))

        val newPhotographer = StaffPhotographer(photographer = "Test Photographer", publication = "Testing")

        val metadataWithUpdatedUsageRights = Some(Edits(usageRights = Some(newPhotographer), metadata = imageWithUsageRights.metadata))

        Await.result(Future.sequence(
          ES.applyImageMetadataOverride(id,
            JsDefined(Json.toJson(metadataWithUpdatedUsageRights)),
            asJsLookup(DateTime.now.withZone(DateTimeZone.UTC)))),
          fiveSeconds)

        reloadedImage(id).get.usageRights.asInstanceOf[StaffPhotographer].photographer shouldEqual "Test Photographer"
      }

      "updating user metadata should update photoshoot suggestions" in {
        val id = UUID.randomUUID().toString
        val imageWithBoringMetadata = createImageForSyndication(id = UUID.randomUUID().toString, true, Some(DateTime.now()), None)

        ES.indexImage(id, Json.toJson(imageWithBoringMetadata))
        eventually(timeout(fiveSeconds), interval(oneHundredMilliseconds))(reloadedImage(id).map(_.id) shouldBe Some(imageWithBoringMetadata.id))

        val newPhotoshoot = Photoshoot("Test photoshoot")

        val updatedMetadata = Some(Edits(photoshoot = Some(newPhotoshoot), metadata = imageWithBoringMetadata.metadata.copy()))
        val updatedLastModifiedDate = DateTime.now.withZone(DateTimeZone.UTC)

        Await.result(Future.sequence(
          ES.applyImageMetadataOverride(id,
            JsDefined(Json.toJson(updatedMetadata)),
            asJsLookup(updatedLastModifiedDate))),
          fiveSeconds)

        reloadedImage(id).flatMap(_.userMetadata.get.photoshoot.map(_.title)) shouldEqual Some("Test photoshoot")
        // TODO how to assert that the suggestion was added?
      }

    }
  }

  private def reloadedImage(id: String) = {
    Thread.sleep(1000)
    Await.result(ES.getImage(id), fiveSeconds)
  }

  private def asJsLookup(d: DateTime): JsLookupResult = JsDefined(Json.toJson(d.toString))

}