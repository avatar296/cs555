package csx55.sta.silver.utils

object SqlTemplateProcessor {

  def inject(sqlTemplate: String, replacements: Map[String, Any]): String = {
    replacements.foldLeft(sqlTemplate) { case (sql, (key, value)) =>
      sql.replace(s"$${$key}", value.toString)
    }
  }

  def validatePlaceholders(sqlTemplate: String, requiredKeys: Set[String]): Set[String] = {
    requiredKeys.filterNot(key => sqlTemplate.contains(s"$${$key}"))
  }
}
