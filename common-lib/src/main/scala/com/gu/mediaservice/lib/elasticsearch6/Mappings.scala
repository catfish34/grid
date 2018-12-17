package com.gu.mediaservice.lib.elasticsearch6

import com.sksamuel.elastic4s.http.ElasticDsl.{mapping, _}
import com.sksamuel.elastic4s.mappings.dynamictemplate.{DynamicMapping, DynamicTemplateRequest}
import com.sksamuel.elastic4s.mappings.{MappingDefinition, ObjectField}
import play.api.libs.json.{JsObject, Json}

object Mappings {

  val dummyType = "_doc" // see https://www.elastic.co/blog/removal-of-mapping-types-elasticsearch

  val imageMapping: MappingDefinition = {
    def storedJsonObjectTemplate: DynamicTemplateRequest = dynamicTemplate("stored_json_object_template").
      mapping(dynamicType().index(false).store(true)).
      pathMatch("fileMetadata.*")

    mapping(dummyType).
      dynamic(DynamicMapping.Strict).
      fields(
        keywordField("id"),
        metadataMapping("metadata"),
        metadataMapping("originalMetadata"),
        usageRightsMapping("usageRights"),
        syndicationRightsMapping("syndicationRights"),
        usageRightsMapping("originalUsageRights"),
        assetMapping("source"),
        assetMapping("thumbnail"),
        assetMapping("optimisedPng"),
        userMetadataMapping("userMetadata"),
        dateField("userMetadataLastModified"),
        dynamicObj("fileMetadata"),
        exportsMapping("exports"),
        dateField("uploadTime"),
        keywordField("uploadedBy"),
        dateField("lastModified"),
        dynamicObj("identifiers"),
        uploadInfoMapping("uploadInfo"),
        simpleSuggester("suggestMetadataCredit"),
        usagesMapping("usages"),
        dateField("usagesLastModified"),
        leasesMapping("leases"),
        collectionMapping("collections")
      ).dynamicTemplates(Seq(storedJsonObjectTemplate))
  }

  def dimensionsMapping(name: String) = nonDynamicObjectField(name).fields(
    intField("width"),
    intField("height")
  )

  def assetMapping(name: String) = nonDynamicObjectField(name).fields(
    nonIndexedString("file"),
    nonIndexedString("secureUrl"),
    intField("size"),
    keywordField("mimeType"),
    dimensionsMapping("dimensions")
  )

  def metadataMapping(name: String): ObjectField = nonDynamicObjectField(name).fields(
    dateField("dateTaken"),
    sStemmerAnalysed("description"),
    standardAnalysed("byline").copyTo("metadata.englishAnalysedCatchAll"),
    standardAnalysed("bylineTitle"),
    sStemmerAnalysed("title"),
    keywordField("credit").copyTo("metadata.englishAnalysedCatchAll"),
    keywordField("creditUri"),
    standardAnalysed("copyright"),
    standardAnalysed("copyrightNotice"),
    standardAnalysed("suppliersReference").copyTo("metadata.englishAnalysedCatchAll"),
    keywordField("source").copyTo("metadata.englishAnalysedCatchAll"),
    nonAnalysedList("keywords").copyTo("metadata.englishAnalysedCatchAll"),
    standardAnalysed("subLocation").copyTo("metadata.englishAnalysedCatchAll"),
    standardAnalysed("city").copyTo("metadata.englishAnalysedCatchAll"),
    standardAnalysed("state").copyTo("metadata.englishAnalysedCatchAll"),
    standardAnalysed("country").copyTo("metadata.englishAnalysedCatchAll"),
    sStemmerAnalysed("englishAnalysedCatchAll")
  )

  def usageRightsMapping(name: String): ObjectField = nonDynamicObjectField(name).fields(
    keywordField("category"),
    standardAnalysed("restrictions"),
    keywordField("supplier"),
    keywordField("suppliersCollection"),
    standardAnalysed("photographer"),
    keywordField("publication"),
    keywordField("creator"),
    keywordField("licence"),
    keywordField("source"),
    keywordField("contentLink"),
    standardAnalysed("suppliers")
  )

  def syndicationRightsPropertiesMapping(name: String): ObjectField = nonDynamicObjectField(name).fields(
    keywordField("propertyCode"),
    dateField("expiredOn"),
    keywordField("value")
  )

  def syndicationRightsListMapping(name: String) = nonDynamicObjectField(name).fields(
    keywordField("rightCode"),
    booleanField("acquired"),
    syndicationRightsPropertiesMapping("properties")
  )

  def suppliersMapping(name: String): ObjectField = nonDynamicObjectField(name).fields(
    keywordField("supplierId"),
    keywordField("supplierName"),
    booleanField("prAgreement")
  )

  def syndicationRightsMapping(name: String) = nonDynamicObjectField(name).fields(
    dateField("published"),
    suppliersMapping("suppliers"),
    syndicationRightsListMapping("rights"),
    booleanField("isInferred")
  )

  def exportsMapping(name: String) = nonDynamicObjectField(name).fields(
    keywordField("id"),
    keywordField("type"),
    keywordField("author"),
    dateField("date"),
    dynamicObj("specification"),
    assetMapping("master"),
    assetMapping("assets")
  )

  def actionDataMapping(name: String) = {
    nonDynamicObjectField(name).fields(
      keywordField("author"),
      dateField("date")
    )
  }

  /*
    val collectionMapping = withIndexName("collection", nonDynamicObj(
      "path" -> nonAnalysedList("collectionPath"),
      "pathId" -> (nonAnalyzedString ++ copyTo("collections.pathHierarchy")),
      "pathHierarchy" -> hierarchyAnalysedString,
      "description" -> nonAnalyzedString,
      "actionData" -> actionDataMapping
    ))*/
  def collectionMapping(name: String) = { // TODO withIndexName appeared to have no effect on the 1.7 index
    nonDynamicObjectField(name).fields(
      nonAnalysedList("path"),
      keywordField("pathId").copyTo("collections.pathHierarchy"),
      hierarchyAnalysed("pathHierarchy"),
      keywordField("description"),
      actionDataMapping("actionData")
    )
  }

  def photoshootMapping(name: String) = {
    nonDynamicObjectField(name).fields(
      keywordField("title"),
      simpleSuggester("suggest")
    )
  }

  def userMetadataMapping(name: String) = nonDynamicObjectField(name).fields(
    booleanField("archived"),
    nonAnalysedList("labels").copyTo("metadata.englishAnalysedCatchAll"),
    metadataMapping("metadata"),
    usageRightsMapping("usageRights"),
    photoshootMapping("photoshoot")
  )

  def uploadInfoMapping(name: String): ObjectField = nonDynamicObjectField(name).fields(
    keywordField("filename")
  )

  def usageReference(name: String): ObjectField = {
    nonDynamicObjectField(name).fields(
      keywordField("type"),
      keywordField("uri"),
      sStemmerAnalysed("name")
    )
  }

  def printUsageSize(name: String): ObjectField = {
    nonDynamicObjectField(name).fields(
      intField("x"),
      intField("y")
    )
  }

  def printUsageMetadata(name: String): ObjectField = {
    nonDynamicObjectField(name).fields(
      keywordField("sectionName"),
      dateField("issueDate"),
      keywordField("storyName"),
      keywordField("publicationCode"),
      keywordField("publicationName"),
      intField("layoutId"),
      intField("edition"),
      printUsageSize("size"),
      keywordField("orderedBy"),
      keywordField("sectionCode"),
      keywordField("notes"),
      keywordField("source")
    )
  }

  def digitalUsageMetadata(name: String): ObjectField = nonDynamicObjectField(name).fields(
    keywordField("webTitle"),
    keywordField("webUrl"),
    keywordField("sectionId"),
    keywordField("composerUrl")
  )

  def syndicationUsageMetadata(name: String): ObjectField = nonDynamicObjectField(name).fields(
    keywordField("partnerName")
  )

  def frontUsageMetadata(name: String): ObjectField = nonDynamicObjectField(name).fields(
    keywordField("addedBy"),
    keywordField("front")
  )

  def usagesMapping(name: String): ObjectField = nonDynamicObjectField(name).
    includeInAll(true). // TODO not properly understood
    fields(
    keywordField("id"),
    sStemmerAnalysed("title"),
    usageReference("references"),
    keywordField("platform"),
    keywordField("media"),
    keywordField("status"),
    dateField("dateAdded"),
    dateField("dateRemoved"),
    dateField("lastModified"),
    printUsageMetadata("printUsageMetadata"),
    digitalUsageMetadata("digitalUsageMetadata"),
    syndicationUsageMetadata("syndicationUsageMetadata"),
    frontUsageMetadata("frontUsageMetadata")
  )

  def leaseMapping(name: String): ObjectField = nonDynamicObjectField(name).fields(
    keywordField("id"),
    keywordField("leasedBy"),
    dateField("startDate"),
    dateField("endDate"),
    keywordField("access"),
    keywordField("active"),
    sStemmerAnalysed("notes"),
    keywordField("mediaId"),
    dateField("createdAt")
  )

  def leasesMapping(name: String): ObjectField = nonDynamicObjectField(name).fields(
    leaseMapping("leases"),
    leaseMapping("current"),
    dateField("lastModified")
  )

  private def nonDynamicObjectField(name: String) = ObjectField(name).dynamic("strict")

  private def dynamicObj(name: String) = objectField(name).dynamic(true)

  private def nonIndexedString(name: String) = textField(name).index(false)

  private def sStemmerAnalysed(name: String) = textField(name).analyzer(IndexSettings.englishSStemmerAnalyzerName)

  private def hierarchyAnalysed(name: String) = textField(name).analyzer(IndexSettings.hierarchyAnalyserName)

  private def standardAnalysed(name: String) = textField(name).analyzer("standard")

  private def simpleSuggester(name: String) = completionField(name).analyzer("simple").searchAnalyzer("simple")

  //def nonAnalysedList(indexName: String) = Json.obj("type" -> "string", "index" -> "not_analyzed", "index_name" -> indexName)
  private def nonAnalysedList(name: String) = {
    keywordField(name) // TODO index_name
  }

  private def withIndexName(indexName: String, obj: JsObject) = Json.obj("index_Name" -> indexName) ++ obj

  // TODO could have kept this bit of indirection
  //val nonAnalyzedString = Json.obj("type" -> "string", "index" -> "not_analyzed")

}