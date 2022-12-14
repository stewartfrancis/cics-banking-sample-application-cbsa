/*
 *
 *    Copyright contributors to the CICS Banking Sample Application (CBSA) project
 *
 */
package com.ibm.cics.cip.bank.springboot.customerservices.controllers;

import javax.validation.Valid;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClient.ResponseSpec;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ibm.cics.cip.bank.springboot.customerservices.ConnectionInfo;
import com.ibm.cics.cip.bank.springboot.customerservices.CustomerServices;
import com.ibm.cics.cip.bank.springboot.customerservices.jsonclasses.accountenquiry.AccountEnquiryForm;
import com.ibm.cics.cip.bank.springboot.customerservices.jsonclasses.accountenquiry.AccountEnquiryJson;
import com.ibm.cics.cip.bank.springboot.customerservices.jsonclasses.createaccount.AccountType;
import com.ibm.cics.cip.bank.springboot.customerservices.jsonclasses.createaccount.CreateAccountForm;
import com.ibm.cics.cip.bank.springboot.customerservices.jsonclasses.createaccount.CreateAccountJson;
import com.ibm.cics.cip.bank.springboot.customerservices.jsonclasses.createcustomer.CreateCustomerForm;
import com.ibm.cics.cip.bank.springboot.customerservices.jsonclasses.createcustomer.CreateCustomerJson;
import com.ibm.cics.cip.bank.springboot.customerservices.jsonclasses.customerenquiry.CustomerEnquiryForm;
import com.ibm.cics.cip.bank.springboot.customerservices.jsonclasses.customerenquiry.CustomerEnquiryJson;
import com.ibm.cics.cip.bank.springboot.customerservices.jsonclasses.deleteaccount.DeleteAccountJson;
import com.ibm.cics.cip.bank.springboot.customerservices.jsonclasses.deletecustomer.DeleteCustomerJson;
import com.ibm.cics.cip.bank.springboot.customerservices.jsonclasses.listaccounts.ListAccJson;
import com.ibm.cics.cip.bank.springboot.customerservices.jsonclasses.updateaccount.UpdateAccountForm;
import com.ibm.cics.cip.bank.springboot.customerservices.jsonclasses.updateaccount.UpdateAccountJson;
import com.ibm.cics.cip.bank.springboot.customerservices.jsonclasses.updatecustomer.UpdateCustomerForm;
import com.ibm.cics.cip.bank.springboot.customerservices.jsonclasses.updatecustomer.UpdateCustomerJson;


// The code in this file is quite repetitive, however a case/swich block would've required too much over-engineering to do
// Ideally I'd only need to send off one class and I'd only get either an account or customer object back to deserialise,
// but all of the objects returned have slightly different formats and/or fields.

@Controller
public class WebController implements WebMvcConfigurer {

    static final String COPYRIGHT =
      "Copyright contributors to the CICS Banking Sample Application (CBSA) project.";

    private static final Logger log = LoggerFactory.getLogger(CustomerServices.class);

    // Customer and account services screen
    @GetMapping(value={"/services", "/"})
    public String showCustServices(Model model) {
        model.addAttribute("contextPath", "");
        return "customerServices";
    }





    // These are numbered based on their actions; Only the first one is commented, as the rest follow the same format.

    // 1. Enquire account

    // Get request for when first navigating to the page
    @GetMapping("/enqacct")
    public String showAcctForm(AccountEnquiryForm accountEnquiryForm) {
        // String relates to the page template found in /src/main/resources/templates
        return "accountEnquiryForm";
    }

    // When the Submit button is pressed, a Post request to the same location is made
    // This function gets its arguments created using magic and the form submitted
    @PostMapping("/enqacct")
    public String returnAcct(@Valid AccountEnquiryForm accountEnquiryForm, BindingResult bindingResult, Model model)
            throws JsonProcessingException {

        // model is passed to the template - it's used to link objects to fields using model.addAttribute()

        // bindingResult generated by trying to place the fields in the templates in the accountEnquiryForm class
        // If it returns with errors, the same page is shown but as there are errors, extra columns are shown with the error message if applicable
        if (bindingResult.hasErrors()) {
            return "accountEnquiryForm";
        }

        // Instantiating a WebClient at either the specified address or the default one
        WebClient client = WebClient.create(ConnectionInfo.getAddressAndPort()
                + "/inqaccz/enquiry/" + accountEnquiryForm.getAcctNumber());

        try {
            ResponseSpec response = client.get().retrieve();
            // Serialise the object and get a response. This would usually run async, however as it's done during a page load it should be synchronous, hence it's appended with .block()
            String responseBody = response.bodyToMono(String.class).block();
            log.info(responseBody);
            // Deserialise the response so it can be interacted with as a plain Java class
            AccountEnquiryJson responseObj = new ObjectMapper().readValue(responseBody, AccountEnquiryJson.class);
            log.info(responseObj.toString());

            // Run through the checks on error codes in the method shown directly below this and every other response method
            // The method throws exceptions based on the error type
            checkIfResponseValidListAcc(responseObj);

            // Set the fields that will be shown in the template. Either the details of the response, or the details of the error.
            model.addAttribute("largeText", "Account Details:");
            model.addAttribute("smallText", responseObj.toPrettyString());
            model.addAttribute("success", true);
        } catch (ItemNotFoundException e) {
            log.info(e.toString());
            model.addAttribute("largeText", "Request Error");
            model.addAttribute("smallText", e.getMessage());
        } catch (WebClientRequestException e) {
            log.info(e.toString());
            model.addAttribute("largeText", "Request Error");
            model.addAttribute("smallText",
                    "Connection refused or failed to resolve; Are you using the right address and port? Is the server running?");
        } catch (Exception e) {
            log.info(e.toString());
            model.addAttribute("largeText", "Request Error");
            model.addAttribute("smallText",
                    "There was an error processing the request; Please try again later or check logs for more info.");
        }

        // There's a hidden box on all templates that displays the results - it depends on the results field below.
        model.addAttribute("results", true);

        // Return the same page with results now, so new enquiries can be performed without going back.
        return "accountEnquiryForm";
    }

    // this one is a nested if statement, however most are case blocks instead.
    public static void checkIfResponseValidListAcc(AccountEnquiryJson response)
    throws ItemNotFoundException {
        if (response.getINQACC_COMMAREA().getINQACC_SUCCESS().equals("N")) {
            if (response.getINQACC_COMMAREA().getINQACC_CUSTNO() == 0) {
                throw new ItemNotFoundException("account");
            }
        }
    }





    // 2. Enquire Customer
    @GetMapping("/enqcust")
    public String showCustForm(CustomerEnquiryForm customerEnquiryForm) {
        return "customerEnquiryForm";
    }

    @PostMapping("/enqcust")
    public String returnCust(@Valid CustomerEnquiryForm customerEnquiryForm, BindingResult bindingResult, Model model)
            throws JsonProcessingException {
        if (bindingResult.hasErrors()) {
            return "customerEnquiryForm";
        }

        WebClient client = WebClient.create(ConnectionInfo.getAddressAndPort()
                + "/inqcustz/enquiry/" + customerEnquiryForm.getCustNumber());

        try {
            ResponseSpec response = client.get().retrieve();
            String responseBody = response.bodyToMono(String.class).block();
            log.info(responseBody);
            CustomerEnquiryJson responseObj = new ObjectMapper().readValue(responseBody, CustomerEnquiryJson.class);
            log.info(responseObj.toString());
            checkIfResponseValidEnqCust(responseObj);
            model.addAttribute("largeText", "Customer Details");
            model.addAttribute("smallText", responseObj.toPrettyString());
        } catch (ItemNotFoundException e) {
            log.info(e.toString());
            model.addAttribute("largeText", "Request Error");
            model.addAttribute("smallText", e.getMessage());
        } catch (WebClientRequestException e) {
            log.info(e.toString());
            model.addAttribute("largeText", "Request Error");
            model.addAttribute("smallText",
                    "Connection refused or failed to resolve; Are you using the right address and port? Is the server running?");
        } catch (Exception e) {
            log.info(e.toString());
            model.addAttribute("largeText", "Request Error");
            model.addAttribute("smallText",
                    "There was an error processing the request; Please try again later or check logs for more info.");
        }

        model.addAttribute("results", true);
        return "customerEnquiryForm";
    }

    public static void checkIfResponseValidEnqCust(CustomerEnquiryJson response)
    throws ItemNotFoundException {
        if (response.getINQCUSTZ().getINQCUST_INQ_SUCCESS().equals("N")) {
            throw new ItemNotFoundException("customer");
        }
    }






    // 3. List all accounts belonging to a customer
    // Similar form to enqCust since we're still only asking for a customer number
    @GetMapping("/listacc")
    public String showListAccForm(CustomerEnquiryForm customerEnquiryForm) {
        return "listAccountsForm";
    }

    @PostMapping("/listacc")
    public String returnListAcc(@Valid CustomerEnquiryForm customerEnquiryForm, BindingResult bindingResult, Model model)
            throws JsonProcessingException {
        if (bindingResult.hasErrors()) {
            return "listAccountsForm";
        }

        WebClient client = WebClient.create(ConnectionInfo.getAddressAndPort()
                + "/inqacccz/list/" + customerEnquiryForm.getCustNumber());

        try {
            ResponseSpec response = client.get().retrieve();
            String responseBody = response.bodyToMono(String.class).block();
            log.info(responseBody);
            ListAccJson responseObj = new ObjectMapper().readValue(responseBody, ListAccJson.class);
            log.info(responseObj.toString());
            checkIfResponseValidListAcc(responseObj);
            model.addAttribute("largeText", "Accounts belonging to customer " + responseObj.getINQACCCZ().getCUSTOMER_NUMBER() + ":");
            model.addAttribute("accounts", responseObj.getINQACCCZ().getACCOUNT_DETAILS());
        } catch (ItemNotFoundException e) {
            log.info(e.toString());
            model.addAttribute("largeText", "Request Error");
            model.addAttribute("smallText", e.getMessage());
        } catch (WebClientRequestException e) {
            log.info(e.toString());
            model.addAttribute("largeText", "Request Error");
            model.addAttribute("smallText",
                    "Connection refused or failed to resolve; Are you using the right address and port? Is the server running?");
        } catch (Exception e) {
            log.info(e.toString());
            model.addAttribute("largeText", "Request Error");
            model.addAttribute("smallText",
                    "There was an error processing the request; Please try again later or check logs for more info.");
        }

        model.addAttribute("results", true);
        return "listAccountsForm";
    }

    public static void checkIfResponseValidListAcc(ListAccJson response)
            throws ItemNotFoundException {
        switch (response.getINQACCCZ().getCUSTOMER_FOUND()) {
            case "N":
                throw new ItemNotFoundException("customer");
            default:
                break;
        }
    }




    // 4. Create an account
    @GetMapping("/createacc")
    public String showCreateAccForm(CreateAccountForm createAccForm, Model model) {
        model.addAttribute("accountTypes", AccountType.values());
        return "createAccountForm";
    }

    @PostMapping("/createacc")
    public String processCreateAcc(@Valid CreateAccountForm createAccForm, BindingResult bindingResult, Model model)
            throws JsonProcessingException {
        if (bindingResult.hasErrors()) {
            model.addAttribute("accountTypes", AccountType.values());
            return "createAccountForm";
        }
        CreateAccountJson transferjson = new CreateAccountJson(createAccForm);

        // Serialise the object to JSON
        log.info(transferjson.toString());
        String jsonString = new ObjectMapper().writeValueAsString(transferjson);
        log.info(jsonString);

        WebClient client = WebClient
                .create(ConnectionInfo.getAddressAndPort() + "/creacc/insert");

        try {
            // Create a response object - body of json, accept json back, and insert the
            // request body created a couple lines up
            ResponseSpec response = client.post().header("content-type", "application/json")
                    .accept(MediaType.APPLICATION_JSON).body(BodyInserters.fromValue(jsonString)).retrieve();
            String responseBody = response.bodyToMono(String.class).block();
            log.info(responseBody);

            // Deserialise into a POJO
            CreateAccountJson responseObj = new ObjectMapper().readValue(responseBody, CreateAccountJson.class);
            log.info(responseObj.toString());

            // Throws out different exceptions depending on the contents
            checkIfResponseValidCreateAcc(responseObj);

            // If successful...
            model.addAttribute("largeText", "Account creation successful");
            model.addAttribute("smallText", ("Details: " + responseObj.toPrettyString()));

            // Otherwise...
        } catch (TooManyAccountsException | ItemNotFoundException e) {
            log.info(e.toString());
            model.addAttribute("largeText", "Account Error");
            model.addAttribute("smallText", e.getMessage());
        } catch (WebClientRequestException e) {
            log.info(e.toString());
            model.addAttribute("largeText", "Connection Error");
            model.addAttribute("smallText",
                    "Connection refused or failed to resolve; Are you using the right address and port? Is the server running?");
        } catch (Exception e) {
            log.info(e.toString());
            e.printStackTrace();
            model.addAttribute("largeText", "Request Error");
            model.addAttribute("smallText",
                    "There was an error processing the request; Please try again later or check logs for more info.");
        }

        model.addAttribute("results", true);

        // If this isn't here, the radio buttons don't show as they're generated using this enum
        model.addAttribute("accountTypes", AccountType.values());

        return "createAccountForm";
    }

    public static void checkIfResponseValidCreateAcc(CreateAccountJson responseObj) throws TooManyAccountsException, ItemNotFoundException {
        if (responseObj.getCREACC().getCOMM_SUCCESS().equals("N")) {
            switch (responseObj.getCREACC().getCOMM_FAIL_CODE()) {
                case "1":
                    throw new ItemNotFoundException("customer");
                case "8":
                    throw new TooManyAccountsException(Integer.parseInt(responseObj.getCREACC().getCOMM_CUSTNO()));
                case "A":
                    throw new IllegalArgumentException("Invalid account type supplied.");
                default:
                    break;
            }
        }
    }




    // 5. Create a customer
    @GetMapping("/createcust")
    public String showCreateCustForm(CreateCustomerForm createCustForm, Model model) {
        return "createCustomerForm";
    }

    @PostMapping("/createcust")
    public String processCreateCust(@Valid CreateCustomerForm createCustForm, BindingResult bindingResult, Model model)
            throws JsonProcessingException {
        if (bindingResult.hasErrors()) {
            return "createCustomerForm";
        }
        
//        if (!createCustForm.isValidTitle()) {
//        	 model.addAttribute("largeText", "Invalid title");
//             model.addAttribute("smallText",
//                     "Customer title must be one of: Mr, Mrs, Miss, Ms, Dr, Drs, Professor, Lord, Sir, Lady");
//            return "createCustomerForm";
//        }
        CreateCustomerJson transferjson = new CreateCustomerJson(createCustForm);

        // Serialise the object to JSON
        log.info(transferjson.toString());
        String jsonString = new ObjectMapper().writeValueAsString(transferjson);
        log.info("Json to be sent:\n" + jsonString);

        // The port is set elsewhere as it changes frequently
        WebClient client = WebClient
                .create(ConnectionInfo.getAddressAndPort() + "/crecust/insert");

        try {
            // Create a response object - body of json, accept json back, and insert the
            // request body created a couple lines up
            ResponseSpec response = client.post().header("content-type", "application/json")
                    .accept(MediaType.APPLICATION_JSON).body(BodyInserters.fromValue(jsonString)).retrieve();
            String responseBody = response.bodyToMono(String.class).block();
            log.info("Response Body: \n" + responseBody);

            // Deserialise into a POJO
            CreateCustomerJson responseObj = new ObjectMapper().readValue(responseBody, CreateCustomerJson.class);
            log.info("Response Json:\n" + responseObj.toString());

            // Throws out different exceptions depending on the contents
            checkIfResponseValidCreateCust(responseObj);

            // If successful...
            model.addAttribute("largeText", "Customer creation successful");
            model.addAttribute("smallText", (responseObj.toPrettyString()));

            // Otherwise...
        } catch (WebClientRequestException e) {
            log.info(e.toString());
            model.addAttribute("largeText", "Connection Error");
            model.addAttribute("smallText",
                    "Connection refused or failed to resolve; Are you using the right address and port? Is the server running?");
        } catch (Exception e) {
            log.info(e.toString());
            model.addAttribute("largeText", "Request Error");
            model.addAttribute("smallText",
                    "There was an error processing the request; Please try again later or check logs for more info.");
        }

        model.addAttribute("results", true);

        return "createCustomerForm";
    }

    public static void checkIfResponseValidCreateCust(CreateCustomerJson responseObj) throws Exception {
        if (responseObj.getCRECUST().getCOMM_FAIL_CODE() != "") {
            switch (Integer.parseInt(responseObj.getCRECUST().getCOMM_FAIL_CODE())) {
            // case 8:
            //     throw new TooManyAccountsException(Integer.parseInt(responseObj.getCRECUST());
            // default:
            //     break;
            }

            throw new Exception("An unexpected error occured");
        }
        
    }





    // 6. Update an account
    @GetMapping("/updateacc")
    public String showUpdateAccountForm(UpdateAccountForm updateAccForm, Model model) {

        // This links the radio buttons on the template to the AccountType enum
        model.addAttribute("accountTypes", AccountType.values());
        return "updateAccountForm";
    }

    @PostMapping("/updateacc")
    public String processCreateAcc(@Valid UpdateAccountForm updateAccountForm, BindingResult bindingResult, Model model)
            throws JsonProcessingException {
        if (bindingResult.hasErrors()) {

            // Must add the accountTypes enum here as well, otherwise the radio buttons disappear on error
            model.addAttribute("accountTypes", AccountType.values());
            return "updateAccountForm";
        }

        UpdateAccountJson transferjson = new UpdateAccountJson(updateAccountForm);

        // Serialise the object to JSON
        log.info(transferjson.toString());
        String jsonString = new ObjectMapper().writeValueAsString(transferjson);
        log.info(jsonString);

        // The port is set elsewhere as it changes frequently
        WebClient client = WebClient
                .create(ConnectionInfo.getAddressAndPort() + "/updacc/update");

        try {
            // Create a response object - body of json, accept json back, and insert the
            // request body created a couple lines up
            ResponseSpec response = client.put().header("content-type", "application/json")
                    .accept(MediaType.APPLICATION_JSON).body(BodyInserters.fromValue(jsonString)).retrieve();
            String responseBody = response.bodyToMono(String.class).block();
            log.info(responseBody);

            // Deserialise into a POJO
            UpdateAccountJson responseObj = new ObjectMapper().readValue(responseBody, UpdateAccountJson.class);
            log.info(responseObj.toString());

            // Throws out different exceptions depending on the contents
            checkIfResponseValidUpdateAcc(responseObj);

            // If successful...
            model.addAttribute("largeText", "Account updated");
            model.addAttribute("smallText", responseObj.toPrettyString());

            // Otherwise...
        } catch (ItemNotFoundException e) {
            log.info(e.toString());
            model.addAttribute("largeText", "Update Error");
            model.addAttribute("smallText", e.getMessage());
        } catch (WebClientRequestException e) {
            log.info(e.toString());
            model.addAttribute("largeText", "Connection Error");
            model.addAttribute("smallText",
                    "Connection refused or failed to resolve; Are you using the right address and port? Is the server running?");
        } catch (Exception e) {
            log.info(e.toString());
            e.printStackTrace();
            model.addAttribute("largeText", "Request Error");
            model.addAttribute("smallText",
                    "There was an error processing the request; Please try again later or check logs for more info.");
        }

        model.addAttribute("results", true);

        // If this isn't here, the radio buttons don't show as they're generated using this enum
        model.addAttribute("accountTypes", AccountType.values());

        return "updateAccountForm";
    }

    public static void checkIfResponseValidUpdateAcc(UpdateAccountJson responseObj) throws ItemNotFoundException {
        if (responseObj.getUPDACC().getCOMM_SUCCESS().equals("N")) {
            throw new ItemNotFoundException("account");
        }
    }




    // 6. Update a customer
    @GetMapping("/updatecust")
    public String showUpdateAccountForm(UpdateCustomerForm updateCustomerForm, Model model) {
        model.addAttribute("accountTypes", AccountType.values());
        return "updateCustomerForm";
    }

    @PostMapping("/updatecust")
    public String processUpdateCust(@Valid UpdateCustomerForm updateCustomerForm, BindingResult bindingResult, Model model)
            throws JsonProcessingException {
        if (bindingResult.hasErrors()) {
            return "updateCustomerForm";
        }

        UpdateCustomerJson transferjson = new UpdateCustomerJson(updateCustomerForm);

        // Serialise the object to JSON
        log.info(transferjson.toString());
        String jsonString = new ObjectMapper().writeValueAsString(transferjson);
        log.info(jsonString);

        // The port is set elsewhere as it changes frequently
        WebClient client = WebClient
                .create(ConnectionInfo.getAddressAndPort() + "/updcust/update");

        try {
            // Create a response object - body of json, accept json back, and insert the
            // request body created a couple lines up
            ResponseSpec response = client.put().header("content-type", "application/json")
                    .accept(MediaType.APPLICATION_JSON).body(BodyInserters.fromValue(jsonString)).retrieve();
            String responseBody = response.bodyToMono(String.class).block();
            log.info(responseBody);

            // Deserialise into a POJO
            UpdateCustomerJson responseObj = new ObjectMapper().readValue(responseBody, UpdateCustomerJson.class);
            log.info(responseObj.toString());

            // Throws out different exceptions depending on the contents
            checkIfResponseValidUpdateCust(responseObj);

            // If successful...
            model.addAttribute("largeText", "Account updated");
            model.addAttribute("smallText", responseObj.toPrettyString());

            // Otherwise...
        } catch (ItemNotFoundException | IllegalArgumentException e) {
            log.info(e.toString());
            model.addAttribute("largeText", "Update Error");
            model.addAttribute("smallText", e.getMessage());
        } catch (WebClientRequestException e) {
            log.info(e.toString());
            model.addAttribute("largeText", "Connection Error");
            model.addAttribute("smallText",
                    "Connection refused or failed to resolve; Are you using the right address and port? Is the server running?");
        } catch (Exception e) {
            log.info(e.toString());
            model.addAttribute("largeText", "Request Error");
            model.addAttribute("smallText",
                    "There was an error processing the request; Please try again later or check logs for more info.");
        }

        model.addAttribute("results", true);

        return "updateCustomerForm";
    }

    public static void checkIfResponseValidUpdateCust(UpdateCustomerJson responseObj) throws ItemNotFoundException, IllegalArgumentException {
        if (responseObj.getUPDCUST().getCOMM_UPD_SUCCESS().equals("N")) {
            switch (responseObj.getUPDCUST().getCOMM_UPD_FAIL_CD()) {
                case "4":
                    throw new IllegalArgumentException("No name and no address supplied. (Are there spaces before both the name and the address?)");
                case "T":
                    throw new IllegalArgumentException("Invalid title; Valid titles are: Professor, Mr, Mrs, Miss, Ms, Dr, Drs, Lord, Sir or Lady.");
                default:
                    break;
            }
            throw new ItemNotFoundException("customer");
        }
    }




    // 8. Delete an account
    @GetMapping("/delacct")
    public String showDelAcctForm(AccountEnquiryForm accountEnquiryForm) {
        return "deleteAccountForm";
    }

    @PostMapping("/delacct")
    public String deleteAcct(@Valid AccountEnquiryForm accountEnquiryForm, BindingResult bindingResult, Model model)
            throws JsonProcessingException {
        if (bindingResult.hasErrors()) {
            return "deleteAccountForm";
        }

        WebClient client = WebClient.create(ConnectionInfo.getAddressAndPort()
                + "/delacc/remove/" + accountEnquiryForm.getAcctNumber());

        try {
            ResponseSpec response = client.delete().retrieve();
            String responseBody = response.bodyToMono(String.class).block();
            log.info(responseBody);
            DeleteAccountJson responseObj = new ObjectMapper().readValue(responseBody, DeleteAccountJson.class);
            log.info(responseObj.toString());
            checkIfResponseValidDeleteAcc(responseObj);
            model.addAttribute("largeText", "Account Deleted");
            model.addAttribute("smallText", responseObj.toPrettyString());
        } catch (ItemNotFoundException e) {
            log.info(e.toString());
            model.addAttribute("largeText", "Request Error");
            model.addAttribute("smallText", e.getMessage());
        } catch (WebClientRequestException e) {
            log.info(e.toString());
            model.addAttribute("largeText", "Request Error");
            model.addAttribute("smallText",
                    "Connection refused or failed to resolve; Are you using the right address and port? Is the server running?");
        } catch (Exception e) {
            log.info(e.toString());
            model.addAttribute("largeText", "Request Error");
            model.addAttribute("smallText",
                    "There was an error processing the request; Please try again later or check logs for more info.");
        }

        model.addAttribute("results", true);
        return "deleteAccountForm";
    }

    public static void checkIfResponseValidDeleteAcc(DeleteAccountJson responseObj) throws ItemNotFoundException {
        switch (responseObj.getDELACC_COMMAREA().getDELACC_DEL_FAIL_CD()) {
            case 1:
                throw new ItemNotFoundException("account");
            default:
                break;
        }
    }
        
    




    // 9. Delete a customer
    @GetMapping("/delcust")
    public String showDelCustForm(CustomerEnquiryForm customerEnquiryForm) {
        return "deleteCustomerForm";
    }

    @PostMapping("/delcust")
    public String deleteCust(@Valid CustomerEnquiryForm customerEnquiryForm, BindingResult bindingResult, Model model)
            throws JsonProcessingException {
        if (bindingResult.hasErrors()) {
            return "deleteCustomerForm";
        }

        WebClient client = WebClient.create(ConnectionInfo.getAddressAndPort()
                + "/delcus/remove/" + String.format(String.format("%10s", customerEnquiryForm.getCustNumber()).replace(" ", "0")));

        try {
            ResponseSpec response = client.delete().retrieve();
            String responseBody = response.bodyToMono(String.class).block();
            log.info(responseBody);
            DeleteCustomerJson responseObj = new ObjectMapper().readValue(responseBody, DeleteCustomerJson.class);
            log.info(responseObj.toString());
            checkIfResponseValidDeleteCust(responseObj);
            model.addAttribute("largeText", "Customer and associated accounts Deleted");
            model.addAttribute("smallText", responseObj.toPrettyString());
        } catch (ItemNotFoundException e) {
            log.info(e.toString());
            model.addAttribute("largeText", "Request Error");
            model.addAttribute("smallText", e.getMessage());
        } catch (WebClientRequestException e) {
            log.info(e.toString());
            model.addAttribute("largeText", "Request Error");
            model.addAttribute("smallText",
                    "Connection refused or failed to resolve; Are you using the right address and port? Is the server running?");
        } catch (Exception e) {
            log.info(e.toString());
            model.addAttribute("largeText", "Request Error");
            model.addAttribute("smallText",
                    "There was an error processing the request; Please try again later or check logs for more info.");
        }

        model.addAttribute("results", true);
        return "deleteCustomerForm";
    }

    public static void checkIfResponseValidDeleteCust(DeleteCustomerJson responseObj) throws ItemNotFoundException {
        switch (responseObj.getDELCUS().getCOMM_DEL_FAIL_CD()) {
            case 1:
                throw new ItemNotFoundException("customer");
            default:
                break;
        }
    }
}

class InsufficientFundsException extends Exception {

    static final String COPYRIGHT =
      "Copyright contributors to the CICS Banking Sample Application (CBSA) project.";

    public InsufficientFundsException() {

        super("Payment rejected: Insufficient funds.");
    }
}

class InvalidAccountTypeException extends Exception {

    static final String COPYRIGHT =
      "Copyright contributors to the CICS Banking Sample Application (CBSA) project.";

    public InvalidAccountTypeException() {


        super("Payment rejected: Invalid account type.");
    }
}


class TooManyAccountsException extends Exception {

    static final String COPYRIGHT =
      "Copyright contributors to the CICS Banking Sample Application (CBSA) project.";

    public TooManyAccountsException(int customerNumber) {

        super("Too many accounts for customer number " + customerNumber + "; Try deleting an account first.");
    }
}

class ItemNotFoundException extends Exception {

    static final String COPYRIGHT =
      "Copyright contributors to the CICS Banking Sample Application (CBSA) project.";

    public ItemNotFoundException(String item) {

        super("The " + item + " you searched for could not be found; Try a different " + item + " number.");
    }
}