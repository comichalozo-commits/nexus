package de.nexus.agent.core.domain.tools

import android.content.ContentProviderOperation
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.ContactsContract
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Tool(
    name = "contacts",
    description = "Search, add, or find contacts on the device. Requires READ_CONTACTS/WRITE_CONTACTS permissions.",
    category = "communication"
)
class ContactsTool(
    private val context: Context
) : Tool {

    override val name: String = "contacts"
    override val description: String = "Search, add, or find contacts on the device."
    override val parameterSchema: JsonSchema = JsonSchema(
        properties = mapOf(
            "action" to PropertySchema("string", "Action to perform", null, enum = listOf("search", "add", "searchByPhone")),
            "name" to PropertySchema("string", "Contact name for search or add", null),
            "phoneNumber" to PropertySchema("string", "Phone number for add or searchByPhone", null),
            "email" to PropertySchema("string", "Email address (for add)", null),
            "maxResults" to PropertySchema("integer", "Maximum results to return (default 10)", 10)
        ),
        required = listOf("action")
    )

    override suspend fun execute(params: ToolExecutionParams): ToolResult = withContext(Dispatchers.IO) {
        val action = params.params["action"] as? String
            ?: return@withContext ToolResult.fail("Parameter 'action' is required")

        when (action) {
            "search" -> searchContacts(params)
            "add" -> addContact(params)
            "searchByPhone" -> searchByPhone(params)
            else -> ToolResult.fail("Unknown action: $action")
        }
    }

    private fun searchContacts(params: ToolExecutionParams): ToolResult {
        val name = params.params["name"] as? String
            ?: return ToolResult.fail("Parameter 'name' is required for search")
        val maxResults = (params.params["maxResults"] as? Number)?.toInt() ?: 10

        if (!params.context.permissionChecker.hasPermission(android.Manifest.permission.READ_CONTACTS)) {
            return ToolResult.fail("READ_CONTACTS permission not granted")
        }

        return try {
            val contacts = mutableListOf<ContactEntry>()
            val selection = "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY} LIKE ?"
            val selectionArgs = arrayOf("%$name%")

            val cursor = context.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(
                    ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY,
                    ContactsContract.CommonDataKinds.Phone.NUMBER,
                    ContactsContract.CommonDataKinds.Phone.TYPE,
                    ContactsContract.CommonDataKinds.Email.ADDRESS,
                    ContactsContract.Contacts.PHOTO_URI
                ),
                selection, selectionArgs,
                "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY} ASC"
            )

            var count = 0
            cursor?.use {
                while (it.moveToNext() && count < maxResults) {
                    val id = it.getString(0)
                    val contactName = it.getString(1) ?: ""
                    val number = it.getString(2) ?: ""
                    val type = it.getInt(3)
                    val email = it.getString(4) ?: ""
                    val photoUri = it.getString(5) ?: ""
                    contacts.add(ContactEntry(id, contactName, number, type, email, photoUri))
                    count++
                }
            }

            if (contacts.isEmpty()) {
                ToolResult.ok("No contacts found matching: $name")
            } else {
                val formatted = contacts.distinctBy { it.id }.joinToString("\n\n") { c ->
                    "• ${c.name}\n  Phone: ${c.phoneNumber}\n  Email: ${c.email.ifEmpty { "(none)" }}"
                }
                ToolResult.ok("Contacts matching '$name' (${contacts.distinctBy { it.id }.size}):\n\n$formatted",
                    mapOf("contacts" to contacts))
            }
        } catch (e: Exception) {
            ToolResult.fail("Failed to search contacts: ${e.message}")
        }
    }

    private fun addContact(params: ToolExecutionParams): ToolResult {
        val name = params.params["name"] as? String
            ?: return ToolResult.fail("Parameter 'name' is required")
        val phoneNumber = params.params["phoneNumber"] as? String ?: ""
        val email = params.params["email"] as? String ?: ""

        if (!params.context.permissionChecker.hasPermission(android.Manifest.permission.WRITE_CONTACTS)) {
            return ToolResult.fail("WRITE_CONTACTS permission not granted")
        }

        return try {
            val ops = ArrayList<ContentProviderOperation>()
            ops.add(
                ContentProviderOperation.newInsert(ContactsContract.RawContacts.CONTENT_URI)
                    .withValue(ContactsContract.RawContacts.ACCOUNT_TYPE, null)
                    .withValue(ContactsContract.RawContacts.ACCOUNT_NAME, null)
                    .build()
            )
            ops.add(
                ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                    .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                    .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE)
                    .withValue(ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME, name)
                    .build()
            )
            if (phoneNumber.isNotEmpty()) {
                ops.add(
                    ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                        .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                        .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE)
                        .withValue(ContactsContract.CommonDataKinds.Phone.NUMBER, phoneNumber)
                        .withValue(ContactsContract.CommonDataKinds.Phone.TYPE, ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE)
                        .build()
                )
            }
            if (email.isNotEmpty()) {
                ops.add(
                    ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                        .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                        .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE)
                        .withValue(ContactsContract.CommonDataKinds.Email.ADDRESS, email)
                        .withValue(ContactsContract.CommonDataKinds.Email.TYPE, ContactsContract.CommonDataKinds.Email.TYPE_HOME)
                        .build()
                )
            }

            val results = context.contentResolver.applyBatch(ContactsContract.AUTHORITY, ops)
            val contactId = if (results.isNotEmpty()) results[0].uri.toString() else "unknown"
            ToolResult.ok("Contact '$name' added successfully.", mapOf("id" to contactId))
        } catch (e: Exception) {
            ToolResult.fail("Failed to add contact: ${e.message}")
        }
    }

    private fun searchByPhone(params: ToolExecutionParams): ToolResult {
        val phoneNumber = params.params["phoneNumber"] as? String
            ?: return ToolResult.fail("Parameter 'phoneNumber' is required for searchByPhone")

        if (!params.context.permissionChecker.hasPermission(android.Manifest.permission.READ_CONTACTS)) {
            return ToolResult.fail("READ_CONTACTS permission not granted")
        }

        return try {
            val uri = Uri.withAppendedPath(
                ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                Uri.encode(phoneNumber)
            )
            val cursor = context.contentResolver.query(
                uri,
                arrayOf(
                    ContactsContract.PhoneLookup._ID,
                    ContactsContract.PhoneLookup.DISPLAY_NAME,
                    ContactsContract.PhoneLookup.NUMBER
                ),
                null, null, null
            )

            val results = mutableListOf<ContactEntry>()
            cursor?.use {
                while (it.moveToNext()) {
                    val id = it.getString(0)
                    val name = it.getString(1) ?: "Unknown"
                    val number = it.getString(2) ?: phoneNumber
                    results.add(ContactEntry(id, name, number, 0, "", ""))
                }
            }

            if (results.isEmpty()) {
                ToolResult.ok("No contact found for: $phoneNumber")
            } else {
                val formatted = results.joinToString("\n") { "• ${it.name} — ${it.phoneNumber}" }
                ToolResult.ok("Contact lookup for $phoneNumber:\n$formatted", mapOf("contacts" to results))
            }
        } catch (e: Exception) {
            ToolResult.fail("Phone lookup failed: ${e.message}")
        }
    }
}

data class ContactEntry(
    val id: String,
    val name: String,
    val phoneNumber: String,
    val phoneType: Int,
    val email: String,
    val photoUri: String
)
