
#ifndef AppServiceLocator_HPP_
#define AppServiceLocator_HPP_

#include <iostream> // TODO: this is to obtain stdlib.. what should be imported instead of this?


#include "PasswordController.hpp"

namespace App
{
    class ServiceLocator
    {
        private:
            ServiceLocator()
            {
                // initialize
                num = -1;
            }
            static ServiceLocator* pInstance;
        //
        public:
            static ServiceLocator& instance()
            {
                if (pInstance == nullptr) {
                    pInstance = new ServiceLocator();
                }
                return *pInstance;
            }
        public:
            std::string documentsPath;
            int num; // TODO: properties
    };
}
App::ServiceLocator* App::ServiceLocator::pInstance = nullptr;

#endif /* AppServiceLocator_HPP_ */
