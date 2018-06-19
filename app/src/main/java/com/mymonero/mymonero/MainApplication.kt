//
//  MainApplication.kt
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

import android.app.Application
import android.content.Context
//
open class ApplicationServiceLocator: ServiceLocator
{
	//
	// Properties - Interface
	val applicationContext: Context by lazy {
		if (MainApplication.instance == null) {
			throw AssertionError("nil MainApplication.instance")
		}
		MainApplication.instance.applicationContext
	}
	//
	lateinit var settingsController: SettingsController
		private set
	lateinit var userIdleController: UserIdleController
		private set
	lateinit var passwordController: PasswordController
		private set
	//
	init
	{
		this.assemble()
	}
	override fun assemble()
	{
		// I. Instantiate
		this.passwordController = PasswordController()
		this.userIdleController = UserIdleController()
		this.settingsController = SettingsController()
		//
		// II. Assemble graph
		this.passwordController.init_applicationContext(this.applicationContext)
		this.passwordController.init_userIdleController(this.userIdleController)
		//
		this.userIdleController.init_idleTimeoutAfterS_settingsProvider(this.settingsController)
		//
		this.settingsController.init_applicationContext(this.applicationContext)
		this.settingsController.init_passwordController(this.passwordController)
		//
		// III. Setup ("build()")
		this.passwordController.setup()
		this.userIdleController.setup()
		this.settingsController.setup()
	}
}
//
class MainApplicationServiceLocator: ApplicationServiceLocator() {}
//
//
class MainApplication : Application()
{
	companion object
	{
		lateinit var instance: MainApplication
		//
		val serviceLocator: MainApplicationServiceLocator by lazy {
			MainApplicationServiceLocator()
		}
	}
	//
	init
	{
		MainApplication.instance = this
	}
	override fun onCreate()
	{
		super.onCreate()
	}
}