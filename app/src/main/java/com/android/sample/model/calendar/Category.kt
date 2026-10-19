// Co-authored-by: OpenAI Codex <noreply@openai.com>
package com.android.sample.model.calendar

/**
 * A user-created calendar category, such as a course or topic.
 *
 * Events reference [id], allowing [name] to change without rewriting events. Repository operations
 * must eventually verify that a referenced category exists and belongs to the event's owner; this
 * value object cannot enforce relationships between stored records.
 *
 * @property id stable category identifier assigned before saving.
 * @property ownerId identifier of the user owning the category.
 * @property name nonblank user-visible label; uniqueness is not enforced by this model.
 */
data class Category(val id: String, val ownerId: String, val name: String) {
  init {
    require(id.isNotBlank()) { "Category ID must not be blank" }
    require(ownerId.isNotBlank()) { "Owner ID must not be blank" }
    require(name.isNotBlank()) { "Category name must not be blank" }
  }
}
