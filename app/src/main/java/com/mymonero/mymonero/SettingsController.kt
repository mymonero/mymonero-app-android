//
//  SettingsController.kt
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
//
package com.mymonero.mymonero
//
interface SettingsProvider {} // for specific domains
interface IdleTimeoutAfterS_SettingsProvider
{
	//
	// Constants - Default values
	val default_appTimeoutAfterS: Long
	//
	// Constants - Special states
	val appTimeoutAfterS_neverValue: Long// = -1
	//
	// Properties
	var appTimeoutAfterS_nullForDefault_orNeverValue: Long?
}
//
enum class DictKey(val rawValue: String)
{
	_id("_id"),
	specificAPIAddressURLAuthority("specificAPIAddressURLAuthority"),
	appTimeoutAfterS_nilForDefault_orNeverValue("appTimeoutAfterS_nilForDefault_orNeverValue"),
	displayCurrencySymbol("displayCurrencySymbol"),
	authentication__requireWhenSending("authentication__requireWhenSending"),
	authentication__requireToShowWalletSecrets("authentication__requireToShowWalletSecrets"),
	authentication__tryBiometric("authentication__tryBiometric");
	//
	val key: String = this.rawValue
	companion object {
		val setForbidden_DictKeys: List<DictKey> = listOf(DictKey._id)
	}
}
//
object SettingsController: IdleTimeoutAfterS_SettingsProvider
{
	//
	// Constants - Default values
	override val default_appTimeoutAfterS: Long = 90 // s …… 30 was a bit short for new users
	val default_displayCurrencySymbol: CurrencySymbol
		get() {
			return Currency.XMR.symbol
		}
	val default_authentication__requireWhenSending = true
	val default_authentication__requireToShowWalletSecrets = true
	val default_authentication__tryBiometric = true

	//
	// Constants - Special states
	override val appTimeoutAfterS_neverValue: Long = -1 // would preferably declare this in the SettingsProvider interface
	//
	// Constants - Persistence
	val collectionName = "Settings"
	//
	// Properties
	override var appTimeoutAfterS_nullForDefault_orNeverValue: Long? = null
	//
	// Properties - Events
	val changed_specificAPIAddressURLAuthority_fns = EventEmitter<SettingsController, String>()
	val changed_appTimeoutAfterS_nilForDefault_orNeverValue_fns = EventEmitter<SettingsController, Long?>()
	val changed_displayCurrencySymbol_fns = EventEmitter<SettingsController, String>()
	val changed_authentication__requireWhenSending_fns = EventEmitter<SettingsController, Boolean>()
	val changed_authentication__requireToShowWalletSecrets_fns = EventEmitter<SettingsController, Boolean>()
	val changed_authentication__tryBiometric_fns = "SettingsController_NotificationNames_Changed_authentication__tryBiometric"


	//
	// Lifecycle - Init
}