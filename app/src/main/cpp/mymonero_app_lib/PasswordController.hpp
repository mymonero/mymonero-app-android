//
//  PasswordController.cpp
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
#include <string>
#include <boost/optional/optional.hpp>
#include <memory>

namespace Passwords
{
    typedef std::string Password;
}
namespace Passwords
{
    enum Type
    { // These are given specific values for the purpose of serialization through the app lib bridge
        PIN = 0,
        password = 1
    };
    //
    static inline std::string new_humanReadableString(Type type)
    {
        return std::string(
            type == PIN ? "PIN" : "password" // TODO: return localized
        );
    }
    static inline std::string capitalized_humanReadableString(Type type)
    {
        std::string str = new_humanReadableString(type);
        str[0] = (char)toupper(str[0]);
        //
        return str;
    }
    static inline std::string new_invalidEntry_humanReadableString(Type type) // TOOD: return localized
    { // TODO: maybe just keep this function at the Android level so it can be localized .. or maybe better to ship localizations with app lib so they're not duplicated everywhere? haven't confirmed C++ lib best practice yet
        return type == PIN ? "Incorrect PIN" : "Incorrect password";
    }
    static inline Type new_detectedFromPassword(Password &password)
    {
        std::string copyOf_password = password;
        copyOf_password.erase(
            std::remove_if(copyOf_password.begin(), copyOf_password.end(), &isdigit),
            copyOf_password.end()
        );
        return copyOf_password.empty() ? Type::PIN : Type::password;
    }
}
namespace Passwords
{
    //
    // Constants
    static const uint32_t minPasswordLength = 6;
    static const uint32_t maxLegal_numberOfTriesDuringThisTimePeriod = 5;
    //
    // Interfaces
    class PasswordProvider
    { // you can use this type for dependency-injecting a Passwords::Controller implementation; see PersistableObject
    public:
        virtual boost::optional<Password> getPassword() const = 0;
    };
    //
    // Controllers
    class Controller: public PasswordProvider
    {
    public:
        //
        // Lifecycle - Init
        Controller(std::string documentsPath)
        {
            this->documentsPath = documentsPath;
        }
        //
        // Constructor args
        std::string documentsPath;
        //
        // Accessors - Interfaces - PasswordProvider
        boost::optional<Password> getPassword() const;
    private:
        //
        // Properties - Instance members
        boost::optional<Password> m_password = boost::none;
    };
}
namespace Passwords
{
    class PasswordControllerEventParticipant
    { // abstract interface - implement with another interface
    public:
        virtual std::string identifier() = 0; // To support isEqual
    };
    static inline bool isEqual(
        PasswordControllerEventParticipant l,
        PasswordControllerEventParticipant r
    ) {
        return l.identifier() == r.identifier();
    }
}
namespace Passwords
{ // EventParticipants
    class WeakRefTo_EventParticipant // TODO: a class is slightly heavyweight for this - anything more like a struct?
    { // use this to construct arrays of event participants w/o having to hold strong references to them
    public: // TODO: does this need to be optional?
        boost::optional<std::weak_ptr<PasswordControllerEventParticipant>> value = boost::none;
    };
    static inline bool isEqual(
        WeakRefTo_EventParticipant l,
        WeakRefTo_EventParticipant r
    ) {
        if (*l.value == boost::none && *r.value == boost::none) {
            return true; // none == none
        } else if (*l.value == boost::none || *r.value == boost::none) {
            return false; // none != !none
        }
        // obtain shared pointers (check weak ptr referent not expired)
        auto l_value_spt = (*l.value).lock();
        auto r_value_spt = (*r.value).lock();
        if (!l_value_spt && !r_value_spt) {
            return true; // null == null
        } else if (!l_value_spt || !r_value_spt) {
            return false; // null != !null
        }
        return (*l_value_spt).identifier() == (*r_value_spt).identifier();
    }
}
namespace Passwords {
    class PasswordEntryDelegate : PasswordControllerEventParticipant
    {
        virtual void getUserToEnterExistingPassword(
                bool isForChangePassword,
                bool isForAuthorizingAppActionOnly, // normally no - this is for things like SendFunds
                boost::optional<std::string> customNavigationBarTitle,
                std::function<void( // TODO: maybe use a better way of returning two optl vals
                    boost::optional<bool>didCancel,
                    boost::optional<Password>obtainedPasswordString)
                > enterExistingPassword_cb
        ) = 0;
        virtual void getUserToEnterNewPasswordAndType(
                bool isForChangePassword,
                std::function<void( // TODO: maybe use a better way of returning two optl vals
                    boost::optional<bool> didCancel,
                    boost::optional<Type> passwordType
                )> enterNewPasswordAndType_cb
        ) = 0;
    };
}
namespace Passwords
{
    class ChangePasswordRegistrant: PasswordControllerEventParticipant
    { // Implement this function to support change-password events as well as revert-from-failed-change-password
    public:
        boost::optional<std::string> passwordController_ChangePassword(); // return err_str:String if error - it will abort and try to revert the changepassword process. at time of writing, this was able to be kept synchronous.
        // TODO: ^-- maybe make this return a code instead of an error string
    };
    class DeleteEverythingRegistrant: PasswordControllerEventParticipant
    {
        boost::optional<std::string> passwordController_DeleteEverything(); // return err_str:String if error. at time of writing, this was able to be kept synchronous.
        // TODO: ^-- maybe make this return a code instead of an error string
    };
}

