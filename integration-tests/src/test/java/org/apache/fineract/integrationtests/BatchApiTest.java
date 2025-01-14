/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.fineract.integrationtests;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.builder.ResponseSpecBuilder;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;
import io.restassured.specification.ResponseSpecification;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.apache.fineract.batch.domain.BatchRequest;
import org.apache.fineract.batch.domain.BatchResponse;
import org.apache.fineract.infrastructure.core.serialization.FromJsonHelper;
import org.apache.fineract.integrationtests.common.BatchHelper;
import org.apache.fineract.integrationtests.common.ClientHelper;
import org.apache.fineract.integrationtests.common.CollateralManagementHelper;
import org.apache.fineract.integrationtests.common.Utils;
import org.apache.fineract.integrationtests.common.loans.LoanProductTestBuilder;
import org.apache.fineract.integrationtests.common.loans.LoanTransactionHelper;
import org.apache.fineract.integrationtests.common.savings.SavingsProductHelper;
import org.apache.fineract.portfolio.loanaccount.domain.LoanStatus;
import org.apache.http.HttpStatus;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Test class for {@link org.apache.fineract.batch.command.CommandStrategyProvider}. This tests the response provided by
 * commandStrategy by injecting it with a {@code BatchRequest}.
 *
 * @author RishabhShukla
 *
 * @see org.apache.fineract.integrationtests.common.BatchHelper
 * @see org.apache.fineract.batch.domain.BatchRequest
 */
public class BatchApiTest {

    private ResponseSpecification responseSpec;
    private RequestSpecification requestSpec;

    /**
     * Sets up the essential settings for the TEST like contentType, expectedStatusCode. It uses the '@BeforeEach'
     * annotation provided by jUnit.
     */
    @BeforeEach
    public void setup() {
        Utils.initializeRESTAssured();
        this.requestSpec = new RequestSpecBuilder().setContentType(ContentType.JSON).build();
        this.requestSpec.header("Authorization", "Basic " + Utils.loginIntoServerAndGetBase64EncodedAuthenticationKey());
        this.responseSpec = new ResponseSpecBuilder().expectStatusCode(200).build();
    }

    /**
     * Tests for the unimplemented command Strategies by returning 501 status code. For a unknownRequest a statusCode
     * 501 is returned back with response.
     *
     * @see org.apache.fineract.batch.command.internal.UnknownCommandStrategy
     */
    @Test
    public void shouldReturnStatusNotImplementedUnknownCommand() {

        final BatchRequest br = new BatchRequest();
        br.setRequestId(4711L);
        br.setRelativeUrl("/nirvana");
        br.setMethod("POST");

        final List<BatchResponse> response = BatchHelper.postWithSingleRequest(this.requestSpec, this.responseSpec, br);

        // Verify that only 501 is returned as the status code
        for (BatchResponse resp : response) {
            Assertions.assertEquals((long) 501, (long) resp.getStatusCode(), "Verify Status code 501");
        }
    }

    /**
     * Tests for the successful response for a createClient request from createClientCommand. A successful response with
     * statusCode '200' is returned back.
     *
     * @see org.apache.fineract.batch.command.internal.CreateClientCommandStrategy
     */
    @Test
    public void shouldReturnOkStatusForCreateClientCommand() {

        final BatchRequest br = BatchHelper.createClientRequest(4712L, "");

        final List<BatchResponse> response = BatchHelper.postWithSingleRequest(this.requestSpec, this.responseSpec, br);

        // Verify that a 200 response is returned as the status code
        for (BatchResponse resp : response) {
            Assertions.assertEquals((long) 200, (long) resp.getStatusCode(), "Verify Status code 200");
        }
    }

    /**
     * Tests for an erroneous response with statusCode '501' if transaction fails. If Query Parameter
     * 'enclosingTransaction' is set to 'true' and if one of the request in BatchRequest fails then all transactions are
     * rolled back.
     *
     * @see org.apache.fineract.batch.command.internal.CreateClientCommandStrategy
     * @see org.apache.fineract.batch.api.BatchesApiResource
     * @see org.apache.fineract.batch.service.BatchApiService
     */
    @Test
    public void shouldRollBackAllTransactionsOnFailure() {

        // Create first client request
        final BatchRequest br1 = BatchHelper.createClientRequest(4713L, "TestExtId11");

        // Create second client request
        final BatchRequest br2 = BatchHelper.createClientRequest(4714L, "TestExtId12");

        // Create third client request, having same externalID as second client,
        // hence cause of error
        final BatchRequest br3 = BatchHelper.createClientRequest(4715L, "TestExtId11");

        final List<BatchRequest> batchRequests = new ArrayList<>();

        batchRequests.add(br1);
        batchRequests.add(br2);
        batchRequests.add(br3);

        final String jsonifiedRequest = BatchHelper.toJsonString(batchRequests);
        final List<BatchResponse> response = BatchHelper.postBatchRequestsWithEnclosingTransaction(this.requestSpec, this.responseSpec,
                jsonifiedRequest);

        // Verifies that none of the client in BatchRequest is created on the
        // server
        BatchHelper.verifyClientCreatedOnServer(this.requestSpec, this.responseSpec, "TestExtId11");
        BatchHelper.verifyClientCreatedOnServer(this.requestSpec, this.responseSpec, "TestExtId12");

        // Asserts that all the transactions have been successfully rolled back
        Assertions.assertEquals(1, response.size());
        Assertions.assertEquals((long) 400, (long) response.get(0).getStatusCode(), "Verify Status code 400");
    }

    /**
     * Tests that a client information was successfully updated through updateClientCommand. A 'changes' parameter is
     * returned in the response after successful update of client information.
     *
     * @see org.apache.fineract.batch.command.internal.UpdateClientCommandStrategy
     */
    @Test
    public void shouldReflectChangesOnClientUpdate() {

        // Create a createClient Request
        final BatchRequest br1 = BatchHelper.createClientRequest(4716L, "");

        // Create a clientUpdate Request
        final BatchRequest br2 = BatchHelper.updateClientRequest(4717L, 4716L);

        final List<BatchRequest> batchRequests = new ArrayList<>();

        batchRequests.add(br1);
        batchRequests.add(br2);

        final String jsonifiedRequest = BatchHelper.toJsonString(batchRequests);

        final List<BatchResponse> response = BatchHelper.postBatchRequestsWithoutEnclosingTransaction(this.requestSpec, this.responseSpec,
                jsonifiedRequest);

        // Get the changes parameter from updateClient Response
        final JsonObject changes = new FromJsonHelper().parse(response.get(1).getBody()).getAsJsonObject().get("changes").getAsJsonObject();

        // Asserts the client information is successfully updated
        Assertions.assertEquals("TestFirstName", changes.get("firstname").getAsString());
        Assertions.assertEquals("TestLastName", changes.get("lastname").getAsString());
    }

    /**
     * Tests that a ApplyLoanCommand was successfully executed and returned a 200(OK) status. It creates a new client
     * and apply a loan to that client. This also verifies the successful resolution of dependencies among two requests.
     *
     * @see org.apache.fineract.batch.command.internal.ApplyLoanCommandStrategy
     */
    @Test
    public void shouldReturnOkStatusForApplyLoanCommand() {

        final String loanProductJSON = new LoanProductTestBuilder() //
                .withPrincipal("10000000.00") //
                .withNumberOfRepayments("24") //
                .withRepaymentAfterEvery("1") //
                .withRepaymentTypeAsMonth() //
                .withinterestRatePerPeriod("2") //
                .withInterestRateFrequencyTypeAsMonths() //
                .withAmortizationTypeAsEqualPrincipalPayment() //
                .withInterestTypeAsDecliningBalance() //
                .currencyDetails("0", "100").build(null);

        final Integer clientID = ClientHelper.createClient(this.requestSpec, this.responseSpec);
        ClientHelper.verifyClientCreatedOnServer(this.requestSpec, this.responseSpec, clientID);

        final Integer collateralId = CollateralManagementHelper.createCollateralProduct(this.requestSpec, this.responseSpec);
        Assertions.assertNotNull(collateralId);
        final Integer clientCollateralId = CollateralManagementHelper.createClientCollateral(this.requestSpec, this.responseSpec,
                String.valueOf(clientID), collateralId);
        Assertions.assertNotNull(clientCollateralId);

        final Integer productId = new LoanTransactionHelper(this.requestSpec, this.responseSpec).getLoanProductId(loanProductJSON);

        // Create a createClient Request
        final BatchRequest br1 = BatchHelper.createClientRequest(4718L, "");

        // Create a activateClient Request
        final BatchRequest br2 = BatchHelper.activateClientRequest(4719L, 4718L);

        // Create a ApplyLoan Request
        final BatchRequest br3 = BatchHelper.applyLoanRequest(4720L, 4719L, productId, clientCollateralId);

        final List<BatchRequest> batchRequests = new ArrayList<>();

        batchRequests.add(br1);
        batchRequests.add(br2);
        batchRequests.add(br3);

        final String jsonifiedRequest = BatchHelper.toJsonString(batchRequests);

        final List<BatchResponse> response = BatchHelper.postBatchRequestsWithoutEnclosingTransaction(this.requestSpec, this.responseSpec,
                jsonifiedRequest);

        // Get the clientId parameter from createClient Response
        final JsonElement clientId = new FromJsonHelper().parse(response.get(0).getBody()).getAsJsonObject().get("clientId");

        Assertions.assertEquals(200L, (long) response.get(1).getStatusCode(), "Verify Status Code 200" + clientId.getAsString());
    }

    /**
     * Tests that a new savings accounts was applied to an existing client and a 200(OK) status was returned. It first
     * creates a new client and a savings product, then uses the cliendId and ProductId to apply a savings account.
     *
     * @see org.apache.fineract.batch.command.internal.ApplySavingsCommandStrategy
     */
    @Test
    public void shouldReturnOkStatusForApplySavingsCommand() {

        final SavingsProductHelper savingsProductHelper = new SavingsProductHelper();
        final String savingsProductJSON = savingsProductHelper //
                .withInterestCompoundingPeriodTypeAsDaily() //
                .withInterestPostingPeriodTypeAsMonthly() //
                .withInterestCalculationPeriodTypeAsDailyBalance() //
                .withMinimumOpenningBalance("5000").build();

        final Integer productId = SavingsProductHelper.createSavingsProduct(savingsProductJSON, this.requestSpec, this.responseSpec);

        // Create a createClient Request
        final BatchRequest br1 = BatchHelper.createClientRequest(4720L, "");

        // Create a activateClient Request
        final BatchRequest br2 = BatchHelper.activateClientRequest(4721L, 4720L);

        // Create a applySavings Request
        final BatchRequest br3 = BatchHelper.applySavingsRequest(4722L, 4721L, productId);

        final List<BatchRequest> batchRequests = new ArrayList<>();

        batchRequests.add(br1);
        batchRequests.add(br2);
        batchRequests.add(br3);

        final String jsonifiedRequest = BatchHelper.toJsonString(batchRequests);

        final List<BatchResponse> response = BatchHelper.postBatchRequestsWithoutEnclosingTransaction(this.requestSpec, this.responseSpec,
                jsonifiedRequest);

        Assertions.assertEquals(200L, (long) response.get(1).getStatusCode(), "Verify Status Code 200");
    }

    /**
     * Tests that a new charge was added to a newly created loan and charges are Collected properly 200(OK) status was
     * returned for successful responses. It first creates a new client and apply a loan, then creates a new charge for
     * the create loan and then fetches all the applied charges
     *
     * @see org.apache.fineract.batch.command.internal.CollectChargesCommandStrategy
     * @see org.apache.fineract.batch.command.internal.CreateChargeCommandStrategy
     */
    @Test
    public void shouldReturnOkStatusForCollectChargesCommand() {

        final String loanProductJSON = new LoanProductTestBuilder() //
                .withPrincipal("10000000.00") //
                .withNumberOfRepayments("24") //
                .withRepaymentAfterEvery("1") //
                .withRepaymentTypeAsMonth() //
                .withinterestRatePerPeriod("2") //
                .withInterestRateFrequencyTypeAsMonths() //
                .withAmortizationTypeAsEqualPrincipalPayment() //
                .withInterestTypeAsDecliningBalance() //
                .currencyDetails("0", "100").build(null);

        final Integer clientID = ClientHelper.createClient(this.requestSpec, this.responseSpec);
        ClientHelper.verifyClientCreatedOnServer(this.requestSpec, this.responseSpec, clientID);

        final Integer collateralId = CollateralManagementHelper.createCollateralProduct(this.requestSpec, this.responseSpec);
        Assertions.assertNotNull(collateralId);
        final Integer clientCollateralId = CollateralManagementHelper.createClientCollateral(this.requestSpec, this.responseSpec,
                String.valueOf(clientID), collateralId);
        Assertions.assertNotNull(clientCollateralId);

        final Integer productId = new LoanTransactionHelper(this.requestSpec, this.responseSpec).getLoanProductId(loanProductJSON);

        // Create a createClient Request
        final BatchRequest br1 = BatchHelper.createClientRequest(4722L, "");

        // Create a activateClient Request
        final BatchRequest br2 = BatchHelper.activateClientRequest(4723L, 4722L);

        // Create a ApplyLoan Request
        final BatchRequest br3 = BatchHelper.applyLoanRequest(4724L, 4723L, productId, clientCollateralId);

        // Create a Collect Charges Request
        final BatchRequest br4 = BatchHelper.collectChargesRequest(4725L, 4724L);

        final List<BatchRequest> batchRequests = new ArrayList<>();

        batchRequests.add(br1);
        batchRequests.add(br2);
        batchRequests.add(br3);
        batchRequests.add(br4);

        final String jsonifiedRequest = BatchHelper.toJsonString(batchRequests);

        final List<BatchResponse> response = BatchHelper.postBatchRequestsWithoutEnclosingTransaction(this.requestSpec, this.responseSpec,
                jsonifiedRequest);

        Assertions.assertEquals(200L, (long) response.get(3).getStatusCode(), "Verify Status Code 200 for Create Loan Charge");
    }

    /**
     * Tests that batch repayment for loans is happening properly. Collected properly 200(OK) status was returned for
     * successful responses. It first creates a new loan and then makes two repayments for it and then verifies that
     * 200(OK) is returned for the repayment requests.
     *
     * @see org.apache.fineract.batch.command.internal.RepayLoanCommandStrategy
     */
    @Test
    public void shouldReturnOkStatusForBatchRepayment() {

        final String loanProductJSON = new LoanProductTestBuilder() //
                .withPrincipal("10000000.00") //
                .withNumberOfRepayments("24") //
                .withRepaymentAfterEvery("1") //
                .withRepaymentTypeAsMonth() //
                .withinterestRatePerPeriod("2") //
                .withInterestRateFrequencyTypeAsMonths() //
                .withAmortizationTypeAsEqualPrincipalPayment() //
                .withInterestTypeAsDecliningBalance() //
                .currencyDetails("0", "100").build(null);

        final Integer clientID = ClientHelper.createClient(this.requestSpec, this.responseSpec);
        ClientHelper.verifyClientCreatedOnServer(this.requestSpec, this.responseSpec, clientID);

        final Integer collateralId = CollateralManagementHelper.createCollateralProduct(this.requestSpec, this.responseSpec);
        Assertions.assertNotNull(collateralId);
        final Integer clientCollateralId = CollateralManagementHelper.createClientCollateral(this.requestSpec, this.responseSpec,
                clientID.toString(), collateralId);
        Assertions.assertNotNull(clientCollateralId);

        final Integer productId = new LoanTransactionHelper(this.requestSpec, this.responseSpec).getLoanProductId(loanProductJSON);

        // Create a createClient Request
        final BatchRequest br1 = BatchHelper.createClientRequest(4730L, "");

        // Create a activateClient Request
        final BatchRequest br2 = BatchHelper.activateClientRequest(4731L, 4730L);

        // Create a ApplyLoan Request
        final BatchRequest br3 = BatchHelper.applyLoanRequest(4732L, 4731L, productId, clientCollateralId);

        // Create a approveLoan Request
        final BatchRequest br4 = BatchHelper.approveLoanRequest(4733L, 4732L);

        // Create a disburseLoan Request
        final BatchRequest br5 = BatchHelper.disburseLoanRequest(4734L, 4733L);

        // Create a loanRepay Request
        final BatchRequest br6 = BatchHelper.repayLoanRequest(4735L, 4734L);

        // Create a loanRepay Request
        final BatchRequest br7 = BatchHelper.repayLoanRequest(4736L, 4734L);

        final List<BatchRequest> batchRequests = new ArrayList<>();

        batchRequests.add(br1);
        batchRequests.add(br2);
        batchRequests.add(br3);
        batchRequests.add(br4);
        batchRequests.add(br5);
        batchRequests.add(br6);
        batchRequests.add(br7);

        final String jsonifiedRequest = BatchHelper.toJsonString(batchRequests);

        final List<BatchResponse> response = BatchHelper.postBatchRequestsWithoutEnclosingTransaction(this.requestSpec, this.responseSpec,
                jsonifiedRequest);

        Assertions.assertEquals(200L, (long) response.get(5).getStatusCode(), "Verify Status Code 200 for Repayment");
        Assertions.assertEquals(200L, (long) response.get(6).getStatusCode(), "Verify Status Code 200 for Repayment");
    }

    /**
     * Test for the successful activation of a pending client using 'ActivateClientCommandStrategy'. A '200' status code
     * is expected on successful activation.
     *
     * @see org.apache.fineract.batch.command.internal.ActivateClientCommandStrategy
     */
    @Test
    public void shouldReturnOkStatusOnSuccessfulClientActivation() {

        // Create a createClient Request
        final BatchRequest br1 = BatchHelper.createClientRequest(4726L, "");

        // Create an activateClient Request
        final BatchRequest br2 = BatchHelper.activateClientRequest(4727L, 4726L);

        final List<BatchRequest> batchRequests = new ArrayList<>();

        batchRequests.add(br1);
        batchRequests.add(br2);

        final String jsonifiedRequest = BatchHelper.toJsonString(batchRequests);

        final List<BatchResponse> response = BatchHelper.postBatchRequestsWithoutEnclosingTransaction(this.requestSpec, this.responseSpec,
                jsonifiedRequest);

        Assertions.assertEquals(200L, (long) response.get(0).getStatusCode(), "Verify Status Code 200 for Create Client");
        Assertions.assertEquals(200L, (long) response.get(1).getStatusCode(), "Verify Status Code 200 for Activate Client");
    }

    /**
     * Test for the successful approval and disbursal of a loan using 'ApproveLoanCommandStrategy' and
     * 'DisburseLoanCommandStrategy'. A '200' status code is expected on successful activation.
     *
     * @see org.apache.fineract.batch.command.internal.ApproveLoanCommandStrategy
     * @see org.apache.fineract.batch.command.internal.DisburseLoanCommandStrategy
     */
    @Test
    public void shouldReturnOkStatusOnSuccessfulLoanApprovalAndDisburse() {
        final String loanProductJSON = new LoanProductTestBuilder() //
                .withPrincipal("10000000.00") //
                .withNumberOfRepayments("24") //
                .withRepaymentAfterEvery("1") //
                .withRepaymentTypeAsMonth() //
                .withinterestRatePerPeriod("2") //
                .withInterestRateFrequencyTypeAsMonths() //
                .withAmortizationTypeAsEqualPrincipalPayment() //
                .withInterestTypeAsDecliningBalance() //
                .currencyDetails("0", "100").build(null);

        final Integer clientID = ClientHelper.createClient(this.requestSpec, this.responseSpec);
        ClientHelper.verifyClientCreatedOnServer(this.requestSpec, this.responseSpec, clientID);

        final Integer collateralId = CollateralManagementHelper.createCollateralProduct(this.requestSpec, this.responseSpec);
        Assertions.assertNotNull(collateralId);
        final Integer clientCollateralId = CollateralManagementHelper.createClientCollateral(this.requestSpec, this.responseSpec,
                String.valueOf(clientID), collateralId);
        Assertions.assertNotNull(clientCollateralId);

        final Integer productId = new LoanTransactionHelper(this.requestSpec, this.responseSpec).getLoanProductId(loanProductJSON);

        // Create a createClient Request
        final BatchRequest br1 = BatchHelper.createClientRequest(4730L, "");

        // Create a activateClient Request
        final BatchRequest br2 = BatchHelper.activateClientRequest(4731L, 4730L);

        // Create an ApplyLoan Request
        final BatchRequest br3 = BatchHelper.applyLoanRequest(4732L, 4731L, productId, clientCollateralId);

        // Create an approveLoan Request
        final BatchRequest br4 = BatchHelper.approveLoanRequest(4733L, 4732L);

        // Create an disburseLoan Request
        final BatchRequest br5 = BatchHelper.disburseLoanRequest(4734L, 4733L);

        final List<BatchRequest> batchRequests = new ArrayList<>();

        batchRequests.add(br1);
        batchRequests.add(br2);
        batchRequests.add(br3);
        batchRequests.add(br4);
        batchRequests.add(br5);

        final String jsonifiedRequest = BatchHelper.toJsonString(batchRequests);

        final List<BatchResponse> response = BatchHelper.postBatchRequestsWithoutEnclosingTransaction(this.requestSpec, this.responseSpec,
                jsonifiedRequest);

        Assertions.assertEquals(200L, (long) response.get(3).getStatusCode(), "Verify Status Code 200 for Approve Loan");
        Assertions.assertEquals(200L, (long) response.get(4).getStatusCode(), "Verify Status Code 200 for Disburse Loan");
    }

    /**
     * Test for the successful create client, apply loan,approval and disbursal of a loan using Batch API with
     * enclosingTransaction. A '200' status code is expected on successful activation.
     *
     * @see org.apache.fineract.batch.command.internal.ApproveLoanCommandStrategy
     * @see org.apache.fineract.batch.command.internal.DisburseLoanCommandStrategy
     */
    @Test
    public void shouldReturnOkStatusOnSuccessfulLoanApprovalAndDisburseWithTransaction() {
        final String loanProductJSON = new LoanProductTestBuilder() //
                .withPrincipal("10000000.00") //
                .withNumberOfRepayments("24") //
                .withRepaymentAfterEvery("1") //
                .withRepaymentTypeAsMonth() //
                .withinterestRatePerPeriod("2") //
                .withInterestRateFrequencyTypeAsMonths() //
                .withAmortizationTypeAsEqualPrincipalPayment() //
                .withInterestTypeAsDecliningBalance() //
                .currencyDetails("0", "100").build(null);

        final Integer clientID = ClientHelper.createClient(this.requestSpec, this.responseSpec);
        ClientHelper.verifyClientCreatedOnServer(this.requestSpec, this.responseSpec, clientID);

        final Integer collateralId = CollateralManagementHelper.createCollateralProduct(this.requestSpec, this.responseSpec);
        Assertions.assertNotNull(collateralId);
        final Integer clientCollateralId = CollateralManagementHelper.createClientCollateral(this.requestSpec, this.responseSpec,
                String.valueOf(clientID), collateralId);
        Assertions.assertNotNull(clientCollateralId);

        final Integer productId = new LoanTransactionHelper(this.requestSpec, this.responseSpec).getLoanProductId(loanProductJSON);

        // Create a createClient Request
        final BatchRequest br1 = BatchHelper.createActiveClientRequest(4740L, "");

        // Create an ApplyLoan Request
        final BatchRequest br2 = BatchHelper.applyLoanRequest(4742L, 4740L, productId, clientCollateralId);

        // Create an approveLoan Request
        final BatchRequest br3 = BatchHelper.approveLoanRequest(4743L, 4742L);

        // Create a disburseLoan Request
        final BatchRequest br4 = BatchHelper.disburseLoanRequest(4744L, 4743L);

        final List<BatchRequest> batchRequests = Arrays.asList(br1, br2, br3, br4);

        final String jsonifiedRequest = BatchHelper.toJsonString(batchRequests);

        final List<BatchResponse> response = BatchHelper.postBatchRequestsWithEnclosingTransaction(this.requestSpec, this.responseSpec,
                jsonifiedRequest);

        Assertions.assertEquals(200L, (long) response.get(0).getStatusCode(), "Verify Status Code 200 for create client");
        Assertions.assertEquals(200L, (long) response.get(1).getStatusCode(), "Verify Status Code 200 for apply Loan");
        Assertions.assertEquals(200L, (long) response.get(2).getStatusCode(), "Verify Status Code 200 for approve Loan");
        Assertions.assertEquals(200L, (long) response.get(3).getStatusCode(), "Verify Status Code 200 for disburse Loan");
    }

    /**
     * Test for the successful disbursement and get loan. A '200' status code is expected on successful responses.
     *
     * @see org.apache.fineract.batch.command.internal.DisburseLoanCommandStrategy
     * @see org.apache.fineract.batch.command.internal.GetTransactionByIdCommandStrategy
     */
    @Test
    public void shouldReturnOkStatusOnSuccessfulDisbursementAndGetTransaction() {
        final String loanProductJSON = new LoanProductTestBuilder() //
                .withPrincipal("10000000.00") //
                .withNumberOfRepayments("24") //
                .withRepaymentAfterEvery("1") //
                .withRepaymentTypeAsMonth() //
                .withinterestRatePerPeriod("2") //
                .withInterestRateFrequencyTypeAsMonths() //
                .withAmortizationTypeAsEqualPrincipalPayment() //
                .withInterestTypeAsDecliningBalance() //
                .currencyDetails("0", "100").build(null);

        final Long applyLoanRequestId = 6730L;
        final Long approveLoanRequestId = 6731L;
        final Long disburseLoanRequestId = 6732L;
        final Long getTransactionRequestId = 6733L;

        // Create product
        final Integer productId = new LoanTransactionHelper(this.requestSpec, this.responseSpec).getLoanProductId(loanProductJSON);

        // Create client
        final Integer clientId = ClientHelper.createClient(this.requestSpec, this.responseSpec);
        ClientHelper.verifyClientCreatedOnServer(this.requestSpec, this.responseSpec, clientId);

        // Create an ApplyLoan Request
        final BatchRequest batchRequest1 = BatchHelper.applyLoanRequestWithClientId(applyLoanRequestId, clientId, productId);

        // Create an approveLoan Request
        final BatchRequest batchRequest2 = BatchHelper.approveLoanRequest(approveLoanRequestId, applyLoanRequestId);

        // Create a disbursement Request
        final BatchRequest batchRequest3 = BatchHelper.disburseLoanRequest(disburseLoanRequestId, approveLoanRequestId);

        // Create a getTransaction Request
        final BatchRequest batchRequest4 = BatchHelper.getTransactionByIdRequest(getTransactionRequestId, disburseLoanRequestId);

        final List<BatchRequest> batchRequests = Arrays.asList(batchRequest1, batchRequest2, batchRequest3, batchRequest4);

        final List<BatchResponse> responses = BatchHelper.postBatchRequestsWithoutEnclosingTransaction(this.requestSpec, this.responseSpec,
                BatchHelper.toJsonString(batchRequests));

        Assertions.assertEquals(HttpStatus.SC_OK, responses.get(0).getStatusCode(), "Verify Status Code 200 for Apply Loan");
        Assertions.assertEquals(HttpStatus.SC_OK, responses.get(1).getStatusCode(), "Verify Status Code 200 for Approve Loan");
        Assertions.assertEquals(HttpStatus.SC_OK, responses.get(2).getStatusCode(), "Verify Status Code 200 for Disburse Loan");
        Assertions.assertEquals(HttpStatus.SC_OK, responses.get(3).getStatusCode(), "Verify Status Code 200 for Get Transaction By Id");
    }

    /**
     * Test for the successful create client, creat, approve and get loan. A '200' status code is expected on successful
     * responses.
     *
     * @see org.apache.fineract.batch.command.internal.CreateClientCommandStrategy
     * @see org.apache.fineract.batch.command.internal.ApplyLoanCommandStrategy
     * @see org.apache.fineract.batch.command.internal.ApproveLoanCommandStrategy
     * @see org.apache.fineract.batch.command.internal.GetLoanByIdCommandStrategy
     */
    @Test
    public void shouldReturnOkStatusOnSuccessfulCreateClientCreateApproveAndGetLoan() {
        final String loanProductJSON = new LoanProductTestBuilder() //
                .withPrincipal("10000000.00") //
                .withNumberOfRepayments("24") //
                .withRepaymentAfterEvery("1") //
                .withRepaymentTypeAsMonth() //
                .withinterestRatePerPeriod("2") //
                .withInterestRateFrequencyTypeAsMonths() //
                .withAmortizationTypeAsEqualPrincipalPayment() //
                .withInterestTypeAsDecliningBalance() //
                .currencyDetails("0", "100").build(null);

        final Long createActiveClientRequestId = 4730L;
        final Long applyLoanRequestId = 4731L;
        final Long approveLoanRequestId = 4732L;
        final Long getLoanByIdRequestId = 4733L;

        // Create product
        final Integer productId = new LoanTransactionHelper(this.requestSpec, this.responseSpec).getLoanProductId(loanProductJSON);

        // Create createClient Request
        final BatchRequest batchRequest1 = BatchHelper.createActiveClientRequest(createActiveClientRequestId, "");

        // Create an ApplyLoan Request
        final BatchRequest batchRequest2 = BatchHelper.applyLoanRequest(applyLoanRequestId, createActiveClientRequestId, productId, null);

        // Create an approveLoan Request
        final BatchRequest batchRequest3 = BatchHelper.approveLoanRequest(approveLoanRequestId, applyLoanRequestId);

        // Get loan by id Request
        final BatchRequest batchRequest4 = BatchHelper.getLoanByIdRequest(getLoanByIdRequestId, applyLoanRequestId, null);

        final List<BatchRequest> batchRequests = Arrays.asList(batchRequest1, batchRequest2, batchRequest3, batchRequest4);

        final List<BatchResponse> responses = BatchHelper.postBatchRequestsWithEnclosingTransaction(this.requestSpec, this.responseSpec,
                BatchHelper.toJsonString(batchRequests));

        Assertions.assertEquals(HttpStatus.SC_OK, responses.get(0).getStatusCode(), "Verify Status Code 200 for Create Client");
        Assertions.assertEquals(HttpStatus.SC_OK, responses.get(1).getStatusCode(), "Verify Status Code 200 for Apply Loan");
        Assertions.assertEquals(HttpStatus.SC_OK, responses.get(2).getStatusCode(), "Verify Status Code 200 for Approve Loan");
        Assertions.assertEquals(HttpStatus.SC_OK, responses.get(3).getStatusCode(), "Verify Status Code 200 for Get Loan By Id");

        final FromJsonHelper jsonHelper = new FromJsonHelper();
        final Long loanId = jsonHelper.extractLongNamed("loanId", jsonHelper.parse(responses.get(1).getBody()).getAsJsonObject());
        final Long loanIdInGetResponse = jsonHelper.extractLongNamed("id", jsonHelper.parse(responses.get(3).getBody()).getAsJsonObject());
        final JsonObject statusInGetResponse = jsonHelper.parse(responses.get(3).getBody()).getAsJsonObject().get("status")
                .getAsJsonObject();

        Assertions.assertEquals(loanId, loanIdInGetResponse);
        Assertions.assertEquals(LoanStatus.APPROVED.getCode(), jsonHelper.extractStringNamed("code", statusInGetResponse));
        Assertions.assertEquals("Approved", jsonHelper.extractStringNamed("value", statusInGetResponse));
    }

    /**
     * Test for the successful creat, approve and get loan. A '200' status code is expected on successful responses.
     *
     * @see org.apache.fineract.batch.command.internal.ApplyLoanCommandStrategy
     * @see org.apache.fineract.batch.command.internal.ApproveLoanCommandStrategy
     * @see org.apache.fineract.batch.command.internal.GetLoanByIdCommandStrategy
     */
    @Test
    public void shouldReturnOkStatusOnSuccessfulCreateApproveAndGetLoan() {
        final String loanProductJSON = new LoanProductTestBuilder() //
                .withPrincipal("10000000.00") //
                .withNumberOfRepayments("24") //
                .withRepaymentAfterEvery("1") //
                .withRepaymentTypeAsMonth() //
                .withinterestRatePerPeriod("2") //
                .withInterestRateFrequencyTypeAsMonths() //
                .withAmortizationTypeAsEqualPrincipalPayment() //
                .withInterestTypeAsDecliningBalance() //
                .currencyDetails("0", "100").build(null);

        final Long applyLoanRequestId = 5730L;
        final Long approveLoanRequestId = 5731L;
        final Long getLoanByIdRequestId = 5732L;
        final Long getLoanByIdWithQueryParametersRequestId = 5733L;

        // Create product
        final Integer productId = new LoanTransactionHelper(this.requestSpec, this.responseSpec).getLoanProductId(loanProductJSON);

        // Create client
        final Integer clientId = ClientHelper.createClient(this.requestSpec, this.responseSpec);
        ClientHelper.verifyClientCreatedOnServer(this.requestSpec, this.responseSpec, clientId);

        // Create an ApplyLoan Request
        final BatchRequest batchRequest1 = BatchHelper.applyLoanRequestWithClientId(applyLoanRequestId, clientId, productId);

        // Create an approveLoan Request
        final BatchRequest batchRequest2 = BatchHelper.approveLoanRequest(approveLoanRequestId, applyLoanRequestId);

        // Get loan by id Request without query param
        final BatchRequest batchRequest3 = BatchHelper.getLoanByIdRequest(getLoanByIdRequestId, applyLoanRequestId, null);

        // Get loan by id Request with query param
        final BatchRequest batchRequest4 = BatchHelper.getLoanByIdRequest(getLoanByIdWithQueryParametersRequestId, applyLoanRequestId,
                "associations=repaymentSchedule,transactions");

        final List<BatchRequest> batchRequests = Arrays.asList(batchRequest1, batchRequest2, batchRequest3, batchRequest4);

        final List<BatchResponse> responses = BatchHelper.postBatchRequestsWithEnclosingTransaction(this.requestSpec, this.responseSpec,
                BatchHelper.toJsonString(batchRequests));

        Assertions.assertEquals(HttpStatus.SC_OK, responses.get(0).getStatusCode(), "Verify Status Code 200 for Apply Loan");
        Assertions.assertEquals(HttpStatus.SC_OK, responses.get(1).getStatusCode(), "Verify Status Code 200 for Approve Loan");
        Assertions.assertEquals(HttpStatus.SC_OK, responses.get(2).getStatusCode(),
                "Verify Status Code 200 for Get Loan By Id without query parameter");
        Assertions.assertEquals(HttpStatus.SC_OK, responses.get(3).getStatusCode(),
                "Verify Status Code 200 for Get Loan By Id with query parameter");

        final FromJsonHelper jsonHelper = new FromJsonHelper();
        final Long loanId = jsonHelper.extractLongNamed("loanId", jsonHelper.parse(responses.get(0).getBody()).getAsJsonObject());
        final Long loanIdInGetResponse = jsonHelper.extractLongNamed("id", jsonHelper.parse(responses.get(2).getBody()).getAsJsonObject());
        final JsonObject statusInGetResponse = jsonHelper.parse(responses.get(2).getBody()).getAsJsonObject().get("status")
                .getAsJsonObject();

        Assertions.assertEquals(loanId, loanIdInGetResponse);
        Assertions.assertEquals(LoanStatus.APPROVED.getCode(), jsonHelper.extractStringNamed("code", statusInGetResponse));
        Assertions.assertEquals("Approved", jsonHelper.extractStringNamed("value", statusInGetResponse));

        // Repayment schedule will not be available in the response
        Assertions.assertFalse(responses.get(2).getBody().contains("repaymentSchedule"));

        // Repayment schedule information will be available in the response based on the query parameter
        Assertions.assertTrue(responses.get(3).getBody().contains("repaymentSchedule"));
    }
}
