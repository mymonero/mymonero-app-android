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

package com.mymonero.KotlinUtils

import java.util.*

typealias EventSubscriptionToken = String

class EventEmitter<TS, TA> // TS: source; TA: arg
{
	companion object {
		private fun new_subscriptionToken(): EventSubscriptionToken = UUID.randomUUID().toString() // hopefully this isn't too slow
	}
	private val invocationMap = mutableMapOf<EventSubscriptionToken, (TS, TA) -> Unit>()
	//
	// Interface
	fun startObserving(m: (TS,TA) -> Unit): EventSubscriptionToken {
		val token = new_subscriptionToken()
		synchronized(invocationMap) {
			invocationMap[token] = m
		}
		//
		return token
	}
	fun stopObserving(token: EventSubscriptionToken) {
		synchronized(invocationMap) {
			invocationMap.remove(token)
		}
	}
	operator fun invoke(source: TS, arg: TA) {
		// call by executing instance of this as if function
		var existing_invocationMap: MutableMap<EventSubscriptionToken, (TS, TA) -> Unit>? = null
		synchronized(invocationMap) { // TODO: is it alright to synchronize the read alone?
			existing_invocationMap = invocationMap
		}
		for ((_, m) in existing_invocationMap!!) {
			m(source, arg)
		}
	}
}
//
object Events {}