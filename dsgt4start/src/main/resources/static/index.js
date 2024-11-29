import {
  initializeApp
} from "https://www.gstatic.com/firebasejs/9.9.4/firebase-app.js";
import {
  getAuth,
  connectAuthEmulator,
  onAuthStateChanged,
  createUserWithEmailAndPassword,
  signInWithEmailAndPassword,
} from "https://www.gstatic.com/firebasejs/9.9.4/firebase-auth.js";

import {setupUserPage} from "./getContent.js";
import {setupToken} from "./header.js";
import * as authentication from "./authentication.js";

// we setup the authentication, and then wire up some key events to event handlers
setupAuth();
wireGuiUpEvents();
wireUpAuthChange();

//setup authentication with local or cloud configuration. 
function setupAuth() {
  let firebaseConfig;
  if (location.hostname === "localhost") {
    firebaseConfig = {
      apiKey: "to be added",
      projectId: "demo-distributed-systems-kul",
    };
  } else {
    firebaseConfig = {
      // TODO: for level 2, paste your config here
    };
  }

  // signout any existing user. Removes any token still in the auth context
  const firebaseApp = initializeApp(firebaseConfig);
  const auth = getAuth(firebaseApp);
  try {
    auth.signOut();
  } catch (err) { }

  // connect to local emulator when running on localhost
  if (location.hostname === "localhost") {
    connectAuthEmulator(auth, "http://localhost:8082", { disableWarnings: true });
  }
}

function wireGuiUpEvents() {
  // Get references to the email and password inputs, and the sign in, sign out and sign up buttons
  var email = document.getElementById("email");
  var password = document.getElementById("password");
  var signInButton = document.getElementById("btnSignIn");
  var signUpButton = document.getElementById("btnSignUp");
  var logoutButton = document.getElementById("btnLogout");

  // Add event listeners to the sign in and sign up buttons
  signInButton.addEventListener("click", function () {
    // Sign in the user using Firebase's signInWithEmailAndPassword method

    signInWithEmailAndPassword(getAuth(), email.value, password.value)
      .then(function () {

        console.log("signedin");
      })
      .catch(function (error) {
        // Show an error message
        console.log("error signInWithEmailAndPassword:")
        console.log(error.message);
        alert(error.message);
      });
  });

  signUpButton.addEventListener("click", function () {
    createUserWithEmailAndPassword(getAuth(), email.value, password.value)
         .then(function (userCredential) {
           return userCredential.user.getIdToken(); // Get the user access token
         })
         .then(function (token) {
           return fetch("/api/newUser", {
             method: 'POST',
             headers: {
               Authorization: 'Bearer ' + token  // Include the token in the Authorization header
             }
           });
         })
         .then(function (response) {
           if (!response.ok) {
             throw new Error(`Error retrieving user info: ${response.text()}`);
           }

           return response.json();
         })
         .then(function (userData) {
           console.log("Current user:", userData); // You can use the user data here
         })
         .catch(function (error) {
           console.error("Error:", error.message);
           alert("Error: " + error.message); // More user-friendly message
         });
     });

  logoutButton.addEventListener("click", function () {
    try {
      var auth = getAuth();
      removeTabsAndLogoutButton()
      auth.signOut();
    } catch (err) { }
  });

}

function wireUpAuthChange() {

  var auth = getAuth();
  onAuthStateChanged(auth, (user) => {
    console.log("onAuthStateChanged: User is", user ? user.email : "null"); // Log user email or null
    if (user == null) {
      console.log("user is null");
      authentication.showUnAuthenticated();
      return;
    }
    if (auth == null) {
      console.log("auth is null");
      authentication.showUnAuthenticated();
      return;
    }
    if (auth.currentUser === undefined || auth.currentUser == null) {
      console.log("currentUser is undefined or null");
      authentication.showUnAuthenticated();
      return;
    }

    auth.currentUser.getIdTokenResult().then((idTokenResult) => {
      // console.log("Hello " + auth.currentUser.email)

      //update GUI when user is authenticated
      authentication.showAuthenticated(auth.currentUser.email);

      // console.log("Token: " + idTokenResult.token);

      //fetch data from server when authentication was successful. 
      var token = idTokenResult.token;
      const isManager = authentication.checkUserRole(token)
      console.log("Is user a manager? ", isManager);
      const logoutButton = document.getElementById("btnLogout");
      logoutButton.style.display = "";


      setupToken(token);
      setupUserPage(token);
    });

  });
}


function removeTabsAndLogoutButton() {
  // Remove all tabs
  const tabs = document.querySelectorAll(".header-tab");
  tabs.forEach(tab => {
    tab.parentNode.removeChild(tab);
  });

  // Remove logout button
  const logoutButton = document.getElementById("btnLogout");
  if (logoutButton) {
    logoutButton.style.display = "none";
  }
}

