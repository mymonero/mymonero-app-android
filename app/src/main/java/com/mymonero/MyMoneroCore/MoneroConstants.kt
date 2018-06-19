//
//  MoneroConstants.kt
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

package com.mymonero.MyMoneroCore

object MoneroConstants
{
	val currency_name = "Monero"
	val currency_symbol = "XMR"
	val currency_requestURIPrefix = "monero:"
	val currency_requestURIPrefix_sansColon = "monero"
	val currency_openAliasPrefix = "xmr" // OpenAlias prefix
	//
	val addressPrefix = 18 // Prefix code for addresses; 18 => addresses start with "4"
	val integratedAddressPrefix = 19 // Prefix code for addresses
	//
	val currency_unitPlaces = 12 // Number of atomic units in one unit of currency. e.g. 12 => 10^12 =
	//
	val txMinConfirms = 10 // Minimum number of confirmations for a transaction to show as confirmed
	val maxBlockNumber: Long = 500000000 // Maximum block number, used for tx unlock time
	val avgBlockTime: Long = 60 // Average block time in seconds, used for unlock time estimation
	//
	val dustThreshold = MoneroAmount("2000000000") // Dust threshold in atomic units; 2*10^9 used for choosing outputs/change - we decompose all the way down if the receiver wants now regardless of threshold
}