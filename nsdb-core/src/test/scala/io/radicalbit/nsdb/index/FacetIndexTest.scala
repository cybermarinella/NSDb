package io.radicalbit.nsdb.index

import java.nio.file.Paths
import java.util.UUID

import cats.scalatest.ValidatedMatchers
import io.radicalbit.nsdb.common.protocol.Bit
import org.apache.lucene.document.LongPoint
import org.apache.lucene.search.{MatchAllDocsQuery, Sort, SortField}
import org.apache.lucene.store.MMapDirectory
import org.scalatest.{FlatSpec, Matchers, OneInstancePerTest}

class FacetIndexTest extends FlatSpec with Matchers with OneInstancePerTest with ValidatedMatchers {

  "FacetIndex" should "write and read properly on disk" in {
    val facetIndex = new FacetIndex(
      new MMapDirectory(Paths.get(s"target/test_index/facet/${UUID.randomUUID}")),
      new MMapDirectory(Paths.get(s"target/test_index/facet/taxo,${UUID.randomUUID}"))
    )

    implicit val writer     = facetIndex.getWriter
    implicit val taxoWriter = facetIndex.getTaxoWriter

    (1 to 100).foreach { i =>
      val testData =
        Bit(timestamp = System.currentTimeMillis, value = 23, dimensions = Map("content" -> s"content_$i"))
      val w = facetIndex.write(testData)
      w shouldBe valid
    }
    taxoWriter.close()
    writer.close()

    implicit val searcher = facetIndex.getSearcher

    val groups = facetIndex.getCount(new MatchAllDocsQuery(), "content", None, Some(100))

    groups.size shouldBe 100
  }

  "FacetIndex" should "write and read properly on disk with multiple dimensions" in {
    val facetIndex = new FacetIndex(
      new MMapDirectory(Paths.get(s"target/test_index/facet/${UUID.randomUUID}")),
      new MMapDirectory(Paths.get(s"target/test_index/facet/taxo,${UUID.randomUUID}"))
    )

    implicit val writer     = facetIndex.getWriter
    implicit val taxoWriter = facetIndex.getTaxoWriter

    (1 to 100).foreach { i =>
      val testData =
        Bit(timestamp = System.currentTimeMillis,
            value = 23,
            dimensions = Map("content" -> s"content_$i", "name" -> s"name_$i"))
      val w = facetIndex.write(testData)
      w shouldBe valid
    }
    taxoWriter.close()
    writer.close()

    implicit val searcher = facetIndex.getSearcher

    val contentGroups = facetIndex.getCount(new MatchAllDocsQuery(), "content", None, Some(100))

    contentGroups.size shouldBe 100

    val nameGroups = facetIndex.getCount(new MatchAllDocsQuery(), "name", None, Some(100))

    nameGroups.size shouldBe 100
  }

  "FacetIndex" should "write and read properly on disk with multiple dimensions and range query" in {
    val facetIndex = new FacetIndex(
      new MMapDirectory(Paths.get(s"target/test_index/facet/${UUID.randomUUID}")),
      new MMapDirectory(Paths.get(s"target/test_index/facet/taxo,${UUID.randomUUID}"))
    )

    implicit val writer     = facetIndex.getWriter
    implicit val taxoWriter = facetIndex.getTaxoWriter

    (1 to 100).foreach { i =>
      val testData =
        Bit(timestamp = i, value = 23, dimensions = Map("content" -> s"content_$i", "name" -> s"name_$i"))
      val w = facetIndex.write(testData)
      w shouldBe valid
    }
    taxoWriter.close()
    writer.close()

    implicit val searcher = facetIndex.getSearcher

    val contentGroups = facetIndex.getCount(LongPoint.newRangeQuery("timestamp", 0, 50), "content", None, Some(100))

    contentGroups.size shouldBe 50

    val nameGroups = facetIndex.getCount(new MatchAllDocsQuery(), "name", None, Some(100))

    nameGroups.size shouldBe 100
  }

  "FacetIndex" should "suppport delete" in {
    val facetIndex = new FacetIndex(
      new MMapDirectory(Paths.get(s"target/test_index/facet/${UUID.randomUUID}")),
      new MMapDirectory(Paths.get(s"target/test_index/facet/taxo,${UUID.randomUUID}"))
    )

    implicit val writer     = facetIndex.getWriter
    implicit val taxoWriter = facetIndex.getTaxoWriter

    (1 to 100).foreach { i =>
      val testData =
        Bit(timestamp = i, value = 23, dimensions = Map("content" -> s"content_$i", "name" -> s"name_$i"))
      val w = facetIndex.write(testData)
      w shouldBe valid
    }
    taxoWriter.close()
    writer.close()

    implicit val searcher = facetIndex.getSearcher

    val nameGroups = facetIndex.getCount(new MatchAllDocsQuery(), "name", None, Some(100))

    nameGroups.size shouldBe 100

    implicit val deleteWriter = facetIndex.getWriter

    facetIndex.delete(
      Bit(timestamp = 100, value = 23, dimensions = Map("content" -> "content_100", "name" -> "name_100")))(
      deleteWriter)

    deleteWriter.close()
    facetIndex.refresh()

    facetIndex.getCount(new MatchAllDocsQuery(), "name", None, Some(100)).size shouldBe 99
  }

  "FacetIndex" should "supports ordering and limiting" in {
    val facetIndex = new FacetIndex(
      new MMapDirectory(Paths.get(s"target/test_index/facet/${UUID.randomUUID}")),
      new MMapDirectory(Paths.get(s"target/test_index/facet/taxo,${UUID.randomUUID}"))
    )

    implicit val writer     = facetIndex.getWriter
    implicit val taxoWriter = facetIndex.getTaxoWriter

    (1 to 100).foreach { i =>
      val factor = i / 4
      val testData =
        Bit(timestamp = i,
            value = factor,
            dimensions = Map("content" -> s"content_$factor", "name" -> s"name_$factor"))
      val w = facetIndex.write(testData)
      w shouldBe valid
    }
    taxoWriter.close()
    writer.close()

    implicit val searcher = facetIndex.getSearcher

    val descSort = new Sort(new SortField("value", SortField.Type.INT, true))

    val contentGroups =
      facetIndex.getCount(LongPoint.newRangeQuery("timestamp", 0, 50), "content", Some(descSort), Some(100))

    contentGroups.size shouldBe 13

    val nameGroups = facetIndex.getCount(new MatchAllDocsQuery(), "name", None, Some(50))

    nameGroups.size shouldBe 26

    nameGroups.head.value shouldBe 4
    nameGroups.last.value shouldBe 1
  }

}
