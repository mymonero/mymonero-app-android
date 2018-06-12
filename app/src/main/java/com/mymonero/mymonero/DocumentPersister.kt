//
//  DocumentPersister.kt
//  MyMonero
//
//  Copyright (c) 2014-2018, MyMonero.com
//
//  All rights reserved.
//
//  Redistribution and use in source and binary forms, with or without modification, are
//  permitted provided that the following conditions are met:
//
//  1. Redistributions of source code must retain the above copyright notice, this list of
//	conditions and the following disclaimer.
//
//  2. Redistributions in binary form must reproduce the above copyright notice, this list
//	of conditions and the following disclaimer in the documentation and/or other
//	materials provided with the distribution.
//
//  3. Neither the name of the copyright holder nor the names of its contributors may be
//	used to endorse or promote products derived from this software without specific
//	prior written permission.
//
//  THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY
//  EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
//  MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL
//  THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
//  SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
//  PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
//  INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
//  STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF
//  THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
//

package com.mymonero.mymonero

import android.util.Log
import java.util.UUID
import android.content.Context
import android.support.v4.provider.DocumentFile
import java.io.*

typealias DocumentId = String
typealias CollectionName = String
typealias DocumentContentString = String
typealias DocumentJSON = Map<String, Any>
typealias MutableDocumentJSON = MutableMap<String, Any>

data class DocumentFileDescription(
	val inCollectionName: CollectionName,
	val documentId: DocumentId
) {
	companion object {
		const val fileKeyComponentDelimiterString = "__"
		const val filenameExt = "MMDBDoc" // just trying to pick something fairly unique, and short
		//
		fun new_documentId(): DocumentId = UUID.randomUUID().toString()
	}
	val new_fileKey: String
		get() = "${this.inCollectionName}${DocumentFileDescription.fileKeyComponentDelimiterString}${this.documentId}"
	val new_filename: String
		get() = "${this.new_fileKey}.${DocumentFileDescription.filenameExt}"
}
object DocumentPersister
{
	//
	// Interface - Accessory classes - Method return values
	data class ErrorOr_DocumentFileDescriptions(
		val err_str: String?,
		val fileDescriptions: List<DocumentFileDescription>?
	)
	data class ErrorOr_DocumentIds(
		val err_str: String?,
		val ids: List<DocumentId>?
	)
	data class ErrorOr_DocumentContentStrings(
		val err_str: String?,
		val strings: List<DocumentContentString>?
	)
	data class ErrorOr_DocumentContentString(
		val err_str: String?,
		val string: String?
	)
	data class ErrorOr_NumRemoved(
		val err_str: String?,
		val numRemoved: Int?
	)
	//
	// Interface - Accessors
	// Or if you are writing the file data directly, read with:
	fun DocumentsData(
		ids: List<DocumentId>,
		collectionName: CollectionName
	): ErrorOr_DocumentContentStrings {
		val fileDescriptions = ids.map{
			DocumentFileDescription(
				inCollectionName = collectionName,
				documentId = it
			)
		}
		return this._read_existentDocumentContentStrings(fileDescriptions)
	}
	fun IdsOfAllDocuments(
		collectionName: CollectionName
	): ErrorOr_DocumentIds {
		val (err_str, fileDescriptions) = this._read_documentFileDescriptions(collectionName)
		if (err_str != null) {
			return ErrorOr_DocumentIds(err_str, null)
		}
		if (fileDescriptions == null) {
			throw AssertionError("nil fileDescriptions")
		}
		var ids = mutableListOf<DocumentId>()
		val unwrapped_fileDescriptions = fileDescriptions as List<DocumentFileDescription>
		for (fileDescription in unwrapped_fileDescriptions) {
			ids.add(fileDescription.documentId)
		}
		//
		return ErrorOr_DocumentIds(null, ids)
	}
	fun AllDocuments(
		collectionName: CollectionName
	): ErrorOr_DocumentContentStrings {
		val (err_str, fileDescriptions) = this._read_documentFileDescriptions(collectionName)
		if (err_str != null) {
			return ErrorOr_DocumentContentStrings(err_str, null)
		}
		assert(fileDescriptions != null)
		//
		return this._read_existentDocumentContentStrings(fileDescriptions!!)
	}
	//
	// Interface - Imperatives
	fun Write(
		documentFileWithString: String, // if you're using this for Documents, be sure to set field _id to id within your fileData
		id: DocumentId, // consumer must supply the document ID since we can't make assumptions about fileData
		collectionName: CollectionName
	): String? { // err_str
		val fileDescription = DocumentFileDescription(
			inCollectionName = collectionName,
			documentId = id
		)
		this._write_fileDescriptionDocumentString(
			fileDescription = fileDescription,
			string = documentFileWithString
		)
		return null // no error string (yet)
	}
	fun RemoveDocuments(
		collectionName: CollectionName,
		ids: List<DocumentId>
	): ErrorOr_NumRemoved {
		var numRemoved = 0
		for (id in ids) {
			val fileDescription = DocumentFileDescription(
				inCollectionName = collectionName,
				documentId = id
			)
			val context = MainApplication.applicationContext()
			val deleted = context.deleteFile(fileDescription.new_filename)
			if (deleted) {
				numRemoved += 1
			}
		}
		assert(numRemoved == ids.count())
		return ErrorOr_NumRemoved(null, numRemoved)
	}
	fun RemoveAllDocuments(
		collectionName: CollectionName
	): ErrorOr_NumRemoved {
		val (err_str, ids) = this.IdsOfAllDocuments(collectionName)
		if (err_str != null) {
			return ErrorOr_NumRemoved(err_str, null)
		}
		return this.RemoveDocuments(collectionName = collectionName, ids = ids!!)
	}
	//
	// Internal - Accessors - Files
	fun _read_documentFileDescriptions(
		collectionName: CollectionName
	): ErrorOr_DocumentFileDescriptions {
		var fileDescriptions = mutableListOf<DocumentFileDescription>()
		val context = MainApplication.applicationContext()
		val listOfFiles = context.fileList()
		// filtering to what should be app DocumentPersister files
		val filenameSuffix = ".${DocumentFileDescription.filenameExt}"
		val dbDocumentFileNames = listOfFiles.filter {
			it.endsWith(suffix = filenameSuffix, ignoreCase = false)
		}
		// going to assume they're not directories - probably is better way to check or pre-filter
		for (filename in dbDocumentFileNames) {
			val filename_sansExt = filename.removeSuffix(filenameSuffix)
			val fileKey = filename_sansExt // assumption
			val fileKey_components = fileKey.split(DocumentFileDescription.fileKeyComponentDelimiterString)
			if (fileKey_components.count() != 2) {
				return ErrorOr_DocumentFileDescriptions(
					"Unrecognized filename format in db data directory.",
					null
				)
			}
			val fileKey_collectionName = fileKey_components[0] // CollectionName
			if (fileKey_collectionName != collectionName) {
				continue
			}
			val fileKey_id  = fileKey_components[1] // DocumentId
			val fileDescription = DocumentFileDescription(
				inCollectionName = fileKey_collectionName,
				documentId = fileKey_id
			)
			fileDescriptions.add(fileDescription) // ought to be a JSON doc file
		}
		return ErrorOr_DocumentFileDescriptions(null, fileDescriptions)
	}
	fun _read_existentDocumentContentStrings(
		documentFileDescriptions: List<DocumentFileDescription>?
	): ErrorOr_DocumentContentStrings {
		var documentContentStrings = mutableListOf<String>()
		if (documentFileDescriptions == null || documentFileDescriptions.count() <= 0) {
			return ErrorOr_DocumentContentStrings(null, documentContentStrings)
		}
		for (documentFileDescription in documentFileDescriptions) {
			val (err_str, contentString) = this.__read_existentDocumentContentString(documentFileDescription)
			if (err_str != null) {
				return ErrorOr_DocumentContentStrings(err_str, null) // immediately
			}
			assert(contentString != null)
			documentContentStrings.add(contentString!!)
		}
		return ErrorOr_DocumentContentStrings(null, documentContentStrings)
	}
	fun __read_existentDocumentContentString(
		documentFileDescription: DocumentFileDescription
	): ErrorOr_DocumentContentString {
		var string: String?
		var fis: FileInputStream? = null
		try {
			fis = MainApplication.applicationContext().openFileInput(
				documentFileDescription.new_filename
			)
			val isr = InputStreamReader(fis)
			val bufferedReader = BufferedReader(isr)
			val sb = StringBuilder()
			while (true) {
				val line = bufferedReader.readLine()
				if (line != null) {
					sb.append(line)
				} else {
					break
				}
			}
			string = sb.toString()
		} finally {
			fis?.close()
		}
		return ErrorOr_DocumentContentString(null, string)
	}
	//
	// Internal - Imperatives - File writing
	fun _write_fileDescriptionDocumentString(
		fileDescription: DocumentFileDescription,
		string: String
	) {
		var fos: FileOutputStream? = null
		try {
			fos = MainApplication.applicationContext().openFileOutput(
				fileDescription.new_filename,
				Context.MODE_PRIVATE
			)
			fos!!.write(string.toByteArray())
		} finally {
			fos?.close()
		}
	}
}