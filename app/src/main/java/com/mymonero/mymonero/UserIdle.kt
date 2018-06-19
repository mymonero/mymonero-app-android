//
//  UserIdle.kt
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

import android.util.Log
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
//
class UserIdleController: BuiltDependency
{
	//
	// Emitters
	val userDidComeBackFromIdle_fns = EventEmitter<UserIdleController, String>()
	val userDidBecomeIdle_fns = EventEmitter<UserIdleController, String>()
	//
	// Properties - Initial
	private lateinit var idleTimeoutAfterS_settingsProvider: IdleTimeoutAfterS_SettingsProvider
	// Properties - Runtime - Interface
	var _isUserIdle = AtomicBoolean(false)
	val isUserIdle: Boolean
		get() = this._isUserIdle.get()
	// Properties - Runtime - Internal
	private var _numberOfSecondsSinceLastUserInteraction = AtomicLong(0)
	private var _numberOfRequestsToLockUserIdleAsDisabled = AtomicInteger(0)
	private var _userIdle_intervalExecutor = ScheduledThreadPoolExecutor(1)
	private var _userIdle_currentIntervalTask_scheduledFuture = AtomicReference<ScheduledFuture<*>?>(null)
	//
	// Lifecycle - Init
	constructor()
	{
		// setup is deferred til after injection
	}
	fun init_idleTimeoutAfterS_settingsProvider(dep: IdleTimeoutAfterS_SettingsProvider)
	{
		this.idleTimeoutAfterS_settingsProvider = dep
	}
	override fun setup()
	{
		if (this.idleTimeoutAfterS_settingsProvider == null) {
			throw AssertionError("UserIdleController missing dependency")
		}
		this.startObserving()
		// ^- let's do the above first
		//
		this._initiate_userIdle_timer()
	}
	fun startObserving()
	{
		// TODO: observe some central dispatch center? or just have activities call UserIdle directly?
//		NotificationCenter.default.addObserver(this, selector: #selector(MMApplication_didSendEvent(_:)), name: MMApplication.NotificationNames.didSendEvent.notificationName, object: null)
	}
	//
	// Lifecycle - Teardown
//	deinit
//	{
//		this.teardown()
//	}
//	fun teardown()
//	{
//		this.stopObserving()
//	}
//	fun stopObserving()
//	{
		// TODO: stop observing if necessary
//		NotificationCenter.default.removeObserver(this, name: MMApplication.NotificationNames.didSendEvent.notificationName, object: null)
//	}
	//
	// Imperatives - Breaking user idle - call these on user activity like touch
	fun breakIdle()
	{
		this._idleBreakingActionOccurred()
	}
	//
	// Imperatives - Interface
	fun temporarilyDisable_userIdle()
	{
		val n = this._numberOfRequestsToLockUserIdleAsDisabled.incrementAndGet()
		if (n == 1) { // if we're requesting to disable without it already having been disabled, i.e. was 0, now 1
			Log.d("App.UserIdle", "Temporarily disabling the user idle timer.")
			this.__disable_userIdle()
		} else {
			Log.d("App.UserIdle", "Requested to temporarily disable user idle but already disabled. Incremented lock.")
		}
	}
	fun reEnable_userIdle()
	{
		var nAfterDecrement: Int = -1
		synchronized(this._numberOfRequestsToLockUserIdleAsDisabled) { // so that no interleaving between validation and decr can happen
			if (this._numberOfRequestsToLockUserIdleAsDisabled.get() == 0) {
				Log.d("App.UserIdle", "ReEnable_userIdle, this._numberOfRequestsToLockUserIdleAsDisabled 0")
				return // don't go below 0
			}
			// ^--- ideally, these two need to be synchronized somehow so no interleaving can happen...
			nAfterDecrement = this._numberOfRequestsToLockUserIdleAsDisabled.decrementAndGet()
		}
		if (nAfterDecrement == -1) {
			throw AssertionError("Unexpected error checking and decrementing user idle lock counter")
		}
		if (nAfterDecrement == 0) {
			Log.d("App.UserIdle", "Re-enabling the user idle timer.")
			this.__reEnable_userIdle()
		} else {
			Log.d("App.UserIdle", "Requested to re-enable user idle but other locks still exist.")
		}
	}
	//
	// Imperatives - Internal
	private fun __disable_userIdle()
	{
		val old_future = this._userIdle_currentIntervalTask_scheduledFuture.getAndSet(null) // free
		if (old_future == null) {
			throw AssertionError("__disable_userIdle called but already have null at this._userIdle_currentIntervalTask_scheduledFuture")
		}
		old_future.cancel(true) // stop and terminate timer thread .. probably necessary every time we're done with the timer
	}
	private fun __reEnable_userIdle()
	{
		if (this._userIdle_currentIntervalTask_scheduledFuture.get() != null) {
			throw AssertionError("__reEnable_userIdle called but non-null this._userIdle_currentIntervalTask_scheduledFuture")
		}
		this._initiate_userIdle_timer()
	}
	//
	private fun _initiate_userIdle_timer()
	{
		val captured_this = this
		val task = Runnable runnable@{
			val sSinceLast = captured_this._numberOfSecondsSinceLastUserInteraction.incrementAndGet() // count the second
			//
			val appTimeoutAfterS = captured_this.idleTimeoutAfterS_settingsProvider.appTimeoutAfterS_nullForDefault_orNeverValue
				?: captured_this.idleTimeoutAfterS_settingsProvider.default_appTimeoutAfterS // use default on no pw entered / no settings info yet
			if (appTimeoutAfterS == captured_this.idleTimeoutAfterS_settingsProvider.appTimeoutAfterS_neverValue) { // then idle timer is specifically disabled
				return@runnable // do nothing
			}
			//
			if (sSinceLast >= appTimeoutAfterS) {
				val wasUserIdle = captured_this._isUserIdle.getAndSet(true)
				if (wasUserIdle == false) { // not already idle (else redundant)
					Log.d("App.UserIdle", "User became idle.")
					captured_this.userDidBecomeIdle_fns.invoke(captured_this, "") // TODO: invoke on some shared thread?
				}
			}
		}
		this._numberOfSecondsSinceLastUserInteraction.set(0) // reset this in case the app disabled user idle at a time at all different from when the last idle breaking action occurred
		val interval_s: Long = 1
		val future = this._userIdle_intervalExecutor.scheduleAtFixedRate(
			task,
			interval_s,
			interval_s,
			TimeUnit.SECONDS
		)
		val old_task = this._userIdle_currentIntervalTask_scheduledFuture.getAndSet(future)
		if (old_task != null) {
			throw AssertionError("Expected this._userIdle_intervalTimer == null")
		}
	}
	//
	// Delegation - Notifications
	private fun MMApplication_didSendEvent()
	{
		// TODO;
		//
		this._idleBreakingActionOccurred()
		// TODO: also detect when app is being controlled w/o touching the screen - e.g. via Simulator keyboard (or perhaps external)…
	}
	//
	// Delegation - Internal
	private fun _idleBreakingActionOccurred()
	{
		val wasUserIdle = this._isUserIdle.getAndSet(false) // mark as having come back from idle
		this._numberOfSecondsSinceLastUserInteraction.set(0) // reset counter
		if (wasUserIdle) { // emit after we have set isUserIdle back to false
			Log.d("App.UserIdle", "User came back from having been idle.")
			this.userDidComeBackFromIdle_fns.invoke(this, "") // TODO: invoke on some shared thread?
		}
	}
}
