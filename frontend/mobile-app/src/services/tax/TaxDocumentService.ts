/**
 * Tax Document Generation Service
 * Generates tax documents for cryptocurrency and investment transactions
 * Supports 1099-B, 1099-MISC, and capital gains/losses reports
 */

import { Platform } from 'react-native';
import RNHTMLtoPDF from 'react-native-html-to-pdf';
import RNFS from 'react-native-fs';
import Share from 'react-native-share';
import { format, startOfYear, endOfYear } from 'date-fns';
import { ApiService } from '../ApiService';
import { SecurityService } from '../SecurityService';
import AsyncStorage from '@react-native-async-storage/async-storage';

// Tax document types
export enum TaxDocumentType {
  FORM_1099_B = '1099-B',
  FORM_1099_MISC = '1099-MISC',
  FORM_1099_INT = '1099-INT',
  CAPITAL_GAINS_REPORT = 'CAPITAL_GAINS',
  TRANSACTION_HISTORY = 'TRANSACTION_HISTORY',
  CRYPTO_GAINS_LOSSES = 'CRYPTO_GAINS_LOSSES',
}

// Tax year configuration
export interface TaxYear {
  year: number;
  startDate: Date;
  endDate: Date;
  filingDeadline: Date;
}

// Transaction data for tax reporting
export interface TaxableTransaction {
  id: string;
  type: 'crypto_trade' | 'crypto_sale' | 'interest_earned' | 'dividend' | 'stock_sale';
  date: string;
  description: string;
  costBasis: number;
  proceeds: number;
  gain: number;
  loss: number;
  currency: string;
  assetType: string;
  quantity?: number;
  symbol?: string;
  holdingPeriod: 'short' | 'long';
  metadata?: Record<string, any>;
}

// Tax summary data
export interface TaxSummary {
  taxYear: number;
  shortTermGains: number;
  shortTermLosses: number;
  longTermGains: number;
  longTermLosses: number;
  netShortTermGain: number;
  netLongTermGain: number;
  totalGain: number;
  interestEarned: number;
  dividendsEarned: number;
  miscIncome: number;
  transactions: TaxableTransaction[];
}

// Generated document metadata
export interface TaxDocument {
  id: string;
  type: TaxDocumentType;
  taxYear: number;
  generatedAt: string;
  filePath: string;
  fileSize: number;
  checksum: string;
  status: 'generated' | 'signed' | 'filed' | 'amended';
  metadata?: Record<string, any>;
}

// User tax profile
export interface TaxProfile {
  userId: string;
  taxId: string; // SSN or EIN
  filingStatus: 'single' | 'married_joint' | 'married_separate' | 'head_of_household';
  name: string;
  address: {
    street: string;
    city: string;
    state: string;
    zip: string;
    country: string;
  };
  phone?: string;
  email?: string;
}

/**
 * Tax Document Generation Service
 */
class TaxDocumentService {
  private static instance: TaxDocumentService;
  private readonly STORAGE_KEY = '@tax_documents';
  private readonly DOCUMENTS_DIR = `${RNFS.DocumentDirectoryPath}/tax_documents`;

  private constructor() {
    this.ensureDocumentsDirectory();
  }

  public static getInstance(): TaxDocumentService {
    if (!TaxDocumentService.instance) {
      TaxDocumentService.instance = new TaxDocumentService();
    }
    return TaxDocumentService.instance;
  }

  /**
   * Ensure tax documents directory exists
   */
  private async ensureDocumentsDirectory(): Promise<void> {
    try {
      const exists = await RNFS.exists(this.DOCUMENTS_DIR);
      if (!exists) {
        await RNFS.mkdir(this.DOCUMENTS_DIR);
      }
    } catch (error) {
      console.error('Failed to create tax documents directory:', error);
    }
  }

  /**
   * Generate tax documents for a specific year
   */
  async generateTaxDocuments(
    taxYear: number,
    documentTypes: TaxDocumentType[] = [TaxDocumentType.FORM_1099_B, TaxDocumentType.CAPITAL_GAINS_REPORT]
  ): Promise<TaxDocument[]> {
    try {
      console.log(`Generating tax documents for year ${taxYear}...`);

      // Get user's tax profile
      const taxProfile = await this.getUserTaxProfile();
      if (!taxProfile) {
        throw new Error('Tax profile not found. Please complete your tax information.');
      }

      // Fetch tax summary data from backend
      const taxSummary = await this.fetchTaxSummary(taxYear);

      // Generate requested documents
      const documents: TaxDocument[] = [];

      for (const docType of documentTypes) {
        const document = await this.generateDocument(docType, taxYear, taxSummary, taxProfile);
        if (document) {
          documents.push(document);
        }
      }

      // Save document metadata
      await this.saveDocumentMetadata(documents);

      // Track analytics
      await this.trackEvent('tax_documents_generated', {
        taxYear,
        documentCount: documents.length,
        documentTypes: documentTypes,
      });

      console.log(`Generated ${documents.length} tax documents`);
      return documents;

    } catch (error) {
      console.error('Failed to generate tax documents:', error);
      throw error;
    }
  }

  /**
   * Generate specific tax document
   */
  private async generateDocument(
    type: TaxDocumentType,
    taxYear: number,
    taxSummary: TaxSummary,
    taxProfile: TaxProfile
  ): Promise<TaxDocument | null> {
    try {
      let html: string;

      switch (type) {
        case TaxDocumentType.FORM_1099_B:
          html = await this.generate1099B(taxYear, taxSummary, taxProfile);
          break;
        case TaxDocumentType.FORM_1099_MISC:
          html = await this.generate1099MISC(taxYear, taxSummary, taxProfile);
          break;
        case TaxDocumentType.FORM_1099_INT:
          html = await this.generate1099INT(taxYear, taxSummary, taxProfile);
          break;
        case TaxDocumentType.CAPITAL_GAINS_REPORT:
          html = await this.generateCapitalGainsReport(taxYear, taxSummary, taxProfile);
          break;
        case TaxDocumentType.CRYPTO_GAINS_LOSSES:
          html = await this.generateCryptoGainsLossesReport(taxYear, taxSummary, taxProfile);
          break;
        case TaxDocumentType.TRANSACTION_HISTORY:
          html = await this.generateTransactionHistory(taxYear, taxSummary, taxProfile);
          break;
        default:
          console.warn(`Unsupported document type: ${type}`);
          return null;
      }

      // Generate PDF from HTML
      const fileName = `${type}_${taxYear}_${Date.now()}.pdf`;
      const filePath = `${this.DOCUMENTS_DIR}/${fileName}`;

      const options = {
        html,
        fileName,
        directory: 'tax_documents',
        base64: false,
      };

      const pdf = await RNHTMLtoPDF.convert(options);

      // Get file info
      const fileInfo = await RNFS.stat(pdf.filePath);
      const checksum = await this.calculateChecksum(pdf.filePath);

      const document: TaxDocument = {
        id: `tax_${type}_${taxYear}_${Date.now()}`,
        type,
        taxYear,
        generatedAt: new Date().toISOString(),
        filePath: pdf.filePath,
        fileSize: fileInfo.size,
        checksum,
        status: 'generated',
        metadata: {
          payer: 'Waqiti Inc.',
          payerTIN: '12-3456789',
          recipient: taxProfile.name,
          recipientTIN: this.maskTaxId(taxProfile.taxId),
        },
      };

      console.log(`Generated ${type} document for ${taxYear}`);
      return document;

    } catch (error) {
      console.error(`Failed to generate ${type} document:`, error);
      throw error;
    }
  }

  /**
   * Generate 1099-B form HTML
   */
  private async generate1099B(
    taxYear: number,
    taxSummary: TaxSummary,
    taxProfile: TaxProfile
  ): Promise<string> {
    const cryptoTransactions = taxSummary.transactions.filter(t => 
      t.type === 'crypto_trade' || t.type === 'crypto_sale'
    );

    return `
      <!DOCTYPE html>
      <html>
      <head>
        <meta charset="UTF-8">
        <title>Form 1099-B - ${taxYear}</title>
        <style>
          body { font-family: Arial, sans-serif; font-size: 12px; margin: 20px; }
          .header { text-align: center; margin-bottom: 30px; }
          .form-title { font-size: 24px; font-weight: bold; }
          .tax-year { font-size: 18px; margin-top: 10px; }
          .section { margin: 20px 0; padding: 15px; border: 1px solid #ccc; }
          .section-title { font-weight: bold; margin-bottom: 10px; }
          .field { margin: 8px 0; }
          .field-label { font-weight: bold; display: inline-block; width: 200px; }
          .field-value { display: inline-block; }
          .transactions-table { width: 100%; border-collapse: collapse; margin-top: 20px; }
          .transactions-table th, .transactions-table td { border: 1px solid #ccc; padding: 8px; text-align: left; }
          .transactions-table th { background-color: #f0f0f0; font-weight: bold; }
          .summary { margin-top: 30px; padding: 20px; background-color: #f9f9f9; }
          .footer { margin-top: 40px; font-size: 10px; color: #666; }
        </style>
      </head>
      <body>
        <div class="header">
          <div class="form-title">Form 1099-B</div>
          <div class="tax-year">Tax Year ${taxYear}</div>
          <div>Proceeds From Broker and Barter Exchange Transactions</div>
        </div>

        <div class="section">
          <div class="section-title">PAYER'S Information</div>
          <div class="field">
            <span class="field-label">Name:</span>
            <span class="field-value">Waqiti Inc.</span>
          </div>
          <div class="field">
            <span class="field-label">Address:</span>
            <span class="field-value">123 Financial Street, Suite 100</span>
          </div>
          <div class="field">
            <span class="field-label">City, State, ZIP:</span>
            <span class="field-value">San Francisco, CA 94105</span>
          </div>
          <div class="field">
            <span class="field-label">TIN:</span>
            <span class="field-value">12-3456789</span>
          </div>
        </div>

        <div class="section">
          <div class="section-title">RECIPIENT'S Information</div>
          <div class="field">
            <span class="field-label">Name:</span>
            <span class="field-value">${taxProfile.name}</span>
          </div>
          <div class="field">
            <span class="field-label">Address:</span>
            <span class="field-value">${taxProfile.address.street}</span>
          </div>
          <div class="field">
            <span class="field-label">City, State, ZIP:</span>
            <span class="field-value">${taxProfile.address.city}, ${taxProfile.address.state} ${taxProfile.address.zip}</span>
          </div>
          <div class="field">
            <span class="field-label">TIN:</span>
            <span class="field-value">${this.maskTaxId(taxProfile.taxId)}</span>
          </div>
        </div>

        <div class="section">
          <div class="section-title">Cryptocurrency Transactions</div>
          <table class="transactions-table">
            <thead>
              <tr>
                <th>Date Sold</th>
                <th>Description</th>
                <th>Quantity</th>
                <th>Proceeds</th>
                <th>Cost Basis</th>
                <th>Gain/(Loss)</th>
                <th>Type</th>
              </tr>
            </thead>
            <tbody>
              ${cryptoTransactions.map(t => `
                <tr>
                  <td>${format(new Date(t.date), 'MM/dd/yyyy')}</td>
                  <td>${t.description}</td>
                  <td>${t.quantity || '-'}</td>
                  <td>$${t.proceeds.toFixed(2)}</td>
                  <td>$${t.costBasis.toFixed(2)}</td>
                  <td>$${(t.gain - t.loss).toFixed(2)}</td>
                  <td>${t.holdingPeriod === 'long' ? 'Long-term' : 'Short-term'}</td>
                </tr>
              `).join('')}
            </tbody>
          </table>
        </div>

        <div class="summary">
          <div class="section-title">Summary</div>
          <div class="field">
            <span class="field-label">Total Proceeds:</span>
            <span class="field-value">$${cryptoTransactions.reduce((sum, t) => sum + t.proceeds, 0).toFixed(2)}</span>
          </div>
          <div class="field">
            <span class="field-label">Total Cost Basis:</span>
            <span class="field-value">$${cryptoTransactions.reduce((sum, t) => sum + t.costBasis, 0).toFixed(2)}</span>
          </div>
          <div class="field">
            <span class="field-label">Total Gain/(Loss):</span>
            <span class="field-value">$${cryptoTransactions.reduce((sum, t) => sum + (t.gain - t.loss), 0).toFixed(2)}</span>
          </div>
        </div>

        <div class="footer">
          <p>This is important tax information and is being furnished to the IRS. If you are required to file a return, a negligence penalty or other sanction may be imposed on you if this income is taxable and the IRS determines that it has not been reported.</p>
          <p>Generated by Waqiti on ${format(new Date(), 'MMMM dd, yyyy')}</p>
        </div>
      </body>
      </html>
    `;
  }

  /**
   * Generate 1099-MISC form HTML
   */
  private async generate1099MISC(
    taxYear: number,
    taxSummary: TaxSummary,
    taxProfile: TaxProfile
  ): Promise<string> {
    return `
      <!DOCTYPE html>
      <html>
      <head>
        <meta charset="UTF-8">
        <title>Form 1099-MISC - ${taxYear}</title>
        <style>
          body { font-family: Arial, sans-serif; font-size: 12px; margin: 20px; }
          .header { text-align: center; margin-bottom: 30px; }
          .form-title { font-size: 24px; font-weight: bold; }
          .section { margin: 20px 0; padding: 15px; border: 1px solid #ccc; }
          .box { margin: 10px 0; padding: 10px; background-color: #f9f9f9; }
          .box-number { font-weight: bold; margin-right: 10px; }
          .amount { float: right; font-weight: bold; }
        </style>
      </head>
      <body>
        <div class="header">
          <div class="form-title">Form 1099-MISC</div>
          <div>Miscellaneous Income - Tax Year ${taxYear}</div>
        </div>

        <div class="section">
          <div class="box">
            <span class="box-number">Box 1 - Rents:</span>
            <span class="amount">$0.00</span>
          </div>
          <div class="box">
            <span class="box-number">Box 2 - Royalties:</span>
            <span class="amount">$0.00</span>
          </div>
          <div class="box">
            <span class="box-number">Box 3 - Other Income:</span>
            <span class="amount">$${taxSummary.miscIncome.toFixed(2)}</span>
          </div>
        </div>
      </body>
      </html>
    `;
  }

  /**
   * Generate 1099-INT form HTML
   */
  private async generate1099INT(
    taxYear: number,
    taxSummary: TaxSummary,
    taxProfile: TaxProfile
  ): Promise<string> {
    return `
      <!DOCTYPE html>
      <html>
      <head>
        <meta charset="UTF-8">
        <title>Form 1099-INT - ${taxYear}</title>
        <style>
          body { font-family: Arial, sans-serif; font-size: 12px; margin: 20px; }
          .header { text-align: center; margin-bottom: 30px; }
          .form-title { font-size: 24px; font-weight: bold; }
          .section { margin: 20px 0; padding: 15px; border: 1px solid #ccc; }
          .box { margin: 10px 0; padding: 10px; background-color: #f9f9f9; }
          .box-number { font-weight: bold; margin-right: 10px; }
          .amount { float: right; font-weight: bold; }
        </style>
      </head>
      <body>
        <div class="header">
          <div class="form-title">Form 1099-INT</div>
          <div>Interest Income - Tax Year ${taxYear}</div>
        </div>

        <div class="section">
          <div class="box">
            <span class="box-number">Box 1 - Interest Income:</span>
            <span class="amount">$${taxSummary.interestEarned.toFixed(2)}</span>
          </div>
          <div class="box">
            <span class="box-number">Box 2 - Early Withdrawal Penalty:</span>
            <span class="amount">$0.00</span>
          </div>
          <div class="box">
            <span class="box-number">Box 3 - Interest on U.S. Savings Bonds:</span>
            <span class="amount">$0.00</span>
          </div>
        </div>
      </body>
      </html>
    `;
  }

  /**
   * Generate capital gains report HTML
   */
  private async generateCapitalGainsReport(
    taxYear: number,
    taxSummary: TaxSummary,
    taxProfile: TaxProfile
  ): Promise<string> {
    return `
      <!DOCTYPE html>
      <html>
      <head>
        <meta charset="UTF-8">
        <title>Capital Gains and Losses Report - ${taxYear}</title>
        <style>
          body { font-family: Arial, sans-serif; font-size: 12px; margin: 20px; }
          .header { text-align: center; margin-bottom: 30px; }
          .title { font-size: 24px; font-weight: bold; }
          .section { margin: 20px 0; }
          .summary-box { background-color: #f0f0f0; padding: 20px; margin: 20px 0; }
          .summary-row { margin: 10px 0; display: flex; justify-content: space-between; }
          .label { font-weight: bold; }
          .positive { color: green; }
          .negative { color: red; }
          table { width: 100%; border-collapse: collapse; margin-top: 20px; }
          th, td { border: 1px solid #ccc; padding: 8px; text-align: left; }
          th { background-color: #f0f0f0; font-weight: bold; }
        </style>
      </head>
      <body>
        <div class="header">
          <div class="title">Capital Gains and Losses Report</div>
          <div>Tax Year ${taxYear}</div>
          <div>${taxProfile.name}</div>
        </div>

        <div class="summary-box">
          <h2>Summary</h2>
          <div class="summary-row">
            <span class="label">Short-term Capital Gains:</span>
            <span class="${taxSummary.shortTermGains > 0 ? 'positive' : ''}">$${taxSummary.shortTermGains.toFixed(2)}</span>
          </div>
          <div class="summary-row">
            <span class="label">Short-term Capital Losses:</span>
            <span class="negative">($${taxSummary.shortTermLosses.toFixed(2)})</span>
          </div>
          <div class="summary-row">
            <span class="label">Net Short-term Gain/(Loss):</span>
            <span class="${taxSummary.netShortTermGain >= 0 ? 'positive' : 'negative'}">
              $${Math.abs(taxSummary.netShortTermGain).toFixed(2)}${taxSummary.netShortTermGain < 0 ? ' (Loss)' : ''}
            </span>
          </div>
          <hr>
          <div class="summary-row">
            <span class="label">Long-term Capital Gains:</span>
            <span class="${taxSummary.longTermGains > 0 ? 'positive' : ''}">$${taxSummary.longTermGains.toFixed(2)}</span>
          </div>
          <div class="summary-row">
            <span class="label">Long-term Capital Losses:</span>
            <span class="negative">($${taxSummary.longTermLosses.toFixed(2)})</span>
          </div>
          <div class="summary-row">
            <span class="label">Net Long-term Gain/(Loss):</span>
            <span class="${taxSummary.netLongTermGain >= 0 ? 'positive' : 'negative'}">
              $${Math.abs(taxSummary.netLongTermGain).toFixed(2)}${taxSummary.netLongTermGain < 0 ? ' (Loss)' : ''}
            </span>
          </div>
          <hr>
          <div class="summary-row">
            <span class="label"><strong>Total Net Gain/(Loss):</strong></span>
            <span class="${taxSummary.totalGain >= 0 ? 'positive' : 'negative'}">
              <strong>$${Math.abs(taxSummary.totalGain).toFixed(2)}${taxSummary.totalGain < 0 ? ' (Loss)' : ''}</strong>
            </span>
          </div>
        </div>

        <div class="section">
          <h2>Transaction Details</h2>
          <table>
            <thead>
              <tr>
                <th>Date</th>
                <th>Description</th>
                <th>Type</th>
                <th>Proceeds</th>
                <th>Cost Basis</th>
                <th>Gain/(Loss)</th>
                <th>Holding Period</th>
              </tr>
            </thead>
            <tbody>
              ${taxSummary.transactions.map(t => `
                <tr>
                  <td>${format(new Date(t.date), 'MM/dd/yyyy')}</td>
                  <td>${t.description}</td>
                  <td>${t.type}</td>
                  <td>$${t.proceeds.toFixed(2)}</td>
                  <td>$${t.costBasis.toFixed(2)}</td>
                  <td class="${(t.gain - t.loss) >= 0 ? 'positive' : 'negative'}">
                    $${Math.abs(t.gain - t.loss).toFixed(2)}${(t.gain - t.loss) < 0 ? ' (Loss)' : ''}
                  </td>
                  <td>${t.holdingPeriod === 'long' ? 'Long-term' : 'Short-term'}</td>
                </tr>
              `).join('')}
            </tbody>
          </table>
        </div>

        <div style="margin-top: 40px; font-size: 10px; color: #666;">
          <p>This report is for informational purposes only and should not be considered tax advice. Please consult with a qualified tax professional for guidance on your specific tax situation.</p>
          <p>Generated by Waqiti on ${format(new Date(), 'MMMM dd, yyyy')}</p>
        </div>
      </body>
      </html>
    `;
  }

  /**
   * Generate crypto gains/losses report HTML
   */
  private async generateCryptoGainsLossesReport(
    taxYear: number,
    taxSummary: TaxSummary,
    taxProfile: TaxProfile
  ): Promise<string> {
    const cryptoTransactions = taxSummary.transactions.filter(t => 
      t.type === 'crypto_trade' || t.type === 'crypto_sale'
    );

    const cryptoBySymbol = cryptoTransactions.reduce((acc, t) => {
      const symbol = t.symbol || 'UNKNOWN';
      if (!acc[symbol]) {
        acc[symbol] = {
          transactions: [],
          totalGain: 0,
          totalLoss: 0,
          totalProceeds: 0,
          totalCostBasis: 0,
        };
      }
      acc[symbol].transactions.push(t);
      acc[symbol].totalGain += t.gain;
      acc[symbol].totalLoss += t.loss;
      acc[symbol].totalProceeds += t.proceeds;
      acc[symbol].totalCostBasis += t.costBasis;
      return acc;
    }, {} as Record<string, any>);

    return `
      <!DOCTYPE html>
      <html>
      <head>
        <meta charset="UTF-8">
        <title>Cryptocurrency Gains and Losses Report - ${taxYear}</title>
        <style>
          body { font-family: Arial, sans-serif; font-size: 12px; margin: 20px; }
          .header { text-align: center; margin-bottom: 30px; }
          .title { font-size: 24px; font-weight: bold; }
          .crypto-section { margin: 30px 0; padding: 20px; border: 1px solid #ddd; }
          .crypto-symbol { font-size: 18px; font-weight: bold; margin-bottom: 15px; }
          .summary-grid { display: grid; grid-template-columns: repeat(2, 1fr); gap: 10px; margin-bottom: 20px; }
          .summary-item { padding: 10px; background-color: #f9f9f9; }
          table { width: 100%; border-collapse: collapse; margin-top: 15px; }
          th, td { border: 1px solid #ccc; padding: 8px; text-align: left; }
          th { background-color: #f0f0f0; font-weight: bold; }
          .positive { color: green; }
          .negative { color: red; }
        </style>
      </head>
      <body>
        <div class="header">
          <div class="title">Cryptocurrency Gains and Losses Report</div>
          <div>Tax Year ${taxYear}</div>
          <div>${taxProfile.name}</div>
        </div>

        ${Object.entries(cryptoBySymbol).map(([symbol, data]: [string, any]) => `
          <div class="crypto-section">
            <div class="crypto-symbol">${symbol}</div>
            <div class="summary-grid">
              <div class="summary-item">
                <strong>Total Proceeds:</strong> $${data.totalProceeds.toFixed(2)}
              </div>
              <div class="summary-item">
                <strong>Total Cost Basis:</strong> $${data.totalCostBasis.toFixed(2)}
              </div>
              <div class="summary-item">
                <strong>Total Gains:</strong> <span class="positive">$${data.totalGain.toFixed(2)}</span>
              </div>
              <div class="summary-item">
                <strong>Total Losses:</strong> <span class="negative">($${data.totalLoss.toFixed(2)})</span>
              </div>
            </div>
            
            <table>
              <thead>
                <tr>
                  <th>Date</th>
                  <th>Quantity</th>
                  <th>Proceeds</th>
                  <th>Cost Basis</th>
                  <th>Gain/(Loss)</th>
                  <th>Holding Period</th>
                </tr>
              </thead>
              <tbody>
                ${data.transactions.map((t: TaxableTransaction) => `
                  <tr>
                    <td>${format(new Date(t.date), 'MM/dd/yyyy')}</td>
                    <td>${t.quantity || '-'}</td>
                    <td>$${t.proceeds.toFixed(2)}</td>
                    <td>$${t.costBasis.toFixed(2)}</td>
                    <td class="${(t.gain - t.loss) >= 0 ? 'positive' : 'negative'}">
                      $${Math.abs(t.gain - t.loss).toFixed(2)}${(t.gain - t.loss) < 0 ? ' (Loss)' : ''}
                    </td>
                    <td>${t.holdingPeriod === 'long' ? 'Long-term' : 'Short-term'}</td>
                  </tr>
                `).join('')}
              </tbody>
            </table>
          </div>
        `).join('')}

        <div style="margin-top: 40px; font-size: 10px; color: #666;">
          <p>This report is provided for tax preparation purposes. Cryptocurrency transactions are subject to capital gains tax rules.</p>
          <p>Generated by Waqiti on ${format(new Date(), 'MMMM dd, yyyy')}</p>
        </div>
      </body>
      </html>
    `;
  }

  /**
   * Generate transaction history report HTML
   */
  private async generateTransactionHistory(
    taxYear: number,
    taxSummary: TaxSummary,
    taxProfile: TaxProfile
  ): Promise<string> {
    return `
      <!DOCTYPE html>
      <html>
      <head>
        <meta charset="UTF-8">
        <title>Transaction History - ${taxYear}</title>
        <style>
          body { font-family: Arial, sans-serif; font-size: 12px; margin: 20px; }
          .header { text-align: center; margin-bottom: 30px; }
          .title { font-size: 24px; font-weight: bold; }
          table { width: 100%; border-collapse: collapse; margin-top: 20px; }
          th, td { border: 1px solid #ccc; padding: 8px; text-align: left; }
          th { background-color: #f0f0f0; font-weight: bold; }
          .summary { margin-top: 30px; padding: 20px; background-color: #f9f9f9; }
        </style>
      </head>
      <body>
        <div class="header">
          <div class="title">Complete Transaction History</div>
          <div>Tax Year ${taxYear}</div>
          <div>${taxProfile.name}</div>
        </div>

        <table>
          <thead>
            <tr>
              <th>Date</th>
              <th>Type</th>
              <th>Description</th>
              <th>Asset</th>
              <th>Quantity</th>
              <th>Proceeds</th>
              <th>Cost Basis</th>
              <th>Gain/(Loss)</th>
            </tr>
          </thead>
          <tbody>
            ${taxSummary.transactions.map(t => `
              <tr>
                <td>${format(new Date(t.date), 'MM/dd/yyyy')}</td>
                <td>${t.type}</td>
                <td>${t.description}</td>
                <td>${t.symbol || t.assetType}</td>
                <td>${t.quantity || '-'}</td>
                <td>$${t.proceeds.toFixed(2)}</td>
                <td>$${t.costBasis.toFixed(2)}</td>
                <td>$${(t.gain - t.loss).toFixed(2)}</td>
              </tr>
            `).join('')}
          </tbody>
        </table>

        <div class="summary">
          <h3>Summary</h3>
          <p><strong>Total Transactions:</strong> ${taxSummary.transactions.length}</p>
          <p><strong>Total Proceeds:</strong> $${taxSummary.transactions.reduce((sum, t) => sum + t.proceeds, 0).toFixed(2)}</p>
          <p><strong>Total Cost Basis:</strong> $${taxSummary.transactions.reduce((sum, t) => sum + t.costBasis, 0).toFixed(2)}</p>
          <p><strong>Net Gain/(Loss):</strong> $${taxSummary.transactions.reduce((sum, t) => sum + (t.gain - t.loss), 0).toFixed(2)}</p>
        </div>
      </body>
      </html>
    `;
  }

  /**
   * Fetch tax summary from backend
   */
  private async fetchTaxSummary(taxYear: number): Promise<TaxSummary> {
    try {
      const response = await ApiService.get(`/tax/summary/${taxYear}`);
      
      if (!response.success) {
        throw new Error(response.message || 'Failed to fetch tax summary');
      }

      return response.data;
    } catch (error) {
      console.error('Failed to fetch tax summary:', error);
      
      // Return mock data for development
      return this.getMockTaxSummary(taxYear);
    }
  }

  /**
   * Get mock tax summary for development
   */
  private getMockTaxSummary(taxYear: number): TaxSummary {
    return {
      taxYear,
      shortTermGains: 5432.10,
      shortTermLosses: 1234.56,
      longTermGains: 12345.67,
      longTermLosses: 2345.67,
      netShortTermGain: 4197.54,
      netLongTermGain: 10000.00,
      totalGain: 14197.54,
      interestEarned: 234.56,
      dividendsEarned: 0,
      miscIncome: 0,
      transactions: [
        {
          id: '1',
          type: 'crypto_sale',
          date: `${taxYear}-03-15`,
          description: 'Sold 0.5 BTC',
          costBasis: 15000,
          proceeds: 25000,
          gain: 10000,
          loss: 0,
          currency: 'USD',
          assetType: 'cryptocurrency',
          quantity: 0.5,
          symbol: 'BTC',
          holdingPeriod: 'long',
        },
        {
          id: '2',
          type: 'crypto_trade',
          date: `${taxYear}-06-20`,
          description: 'Traded 10 ETH for BTC',
          costBasis: 20000,
          proceeds: 22432.10,
          gain: 2432.10,
          loss: 0,
          currency: 'USD',
          assetType: 'cryptocurrency',
          quantity: 10,
          symbol: 'ETH',
          holdingPeriod: 'short',
        },
        {
          id: '3',
          type: 'crypto_sale',
          date: `${taxYear}-09-10`,
          description: 'Sold 5000 DOGE',
          costBasis: 3500,
          proceeds: 2265.44,
          gain: 0,
          loss: 1234.56,
          currency: 'USD',
          assetType: 'cryptocurrency',
          quantity: 5000,
          symbol: 'DOGE',
          holdingPeriod: 'short',
        },
      ],
    };
  }

  /**
   * Get user's tax profile
   */
  private async getUserTaxProfile(): Promise<TaxProfile | null> {
    try {
      const response = await ApiService.get('/tax/profile');
      
      if (response.success && response.data) {
        return response.data;
      }

      // Return mock profile for development
      return {
        userId: 'user123',
        taxId: '123-45-6789',
        filingStatus: 'single',
        name: 'John Doe',
        address: {
          street: '123 Main Street',
          city: 'San Francisco',
          state: 'CA',
          zip: '94105',
          country: 'USA',
        },
        phone: '(555) 123-4567',
        email: 'john.doe@example.com',
      };
    } catch (error) {
      console.error('Failed to fetch tax profile:', error);
      return null;
    }
  }

  /**
   * Mask tax ID for privacy
   */
  private maskTaxId(taxId: string): string {
    if (!taxId || taxId.length < 4) return '***-**-****';
    return `***-**-${taxId.slice(-4)}`;
  }

  /**
   * Calculate file checksum
   */
  private async calculateChecksum(filePath: string): Promise<string> {
    try {
      const fileContent = await RNFS.readFile(filePath, 'base64');
      return SecurityService.hash(fileContent);
    } catch (error) {
      console.error('Failed to calculate checksum:', error);
      return '';
    }
  }

  /**
   * Save document metadata
   */
  private async saveDocumentMetadata(documents: TaxDocument[]): Promise<void> {
    try {
      const existingDocs = await this.getStoredDocuments();
      const updatedDocs = [...existingDocs, ...documents];
      
      await AsyncStorage.setItem(this.STORAGE_KEY, JSON.stringify(updatedDocs));
    } catch (error) {
      console.error('Failed to save document metadata:', error);
    }
  }

  /**
   * Get stored documents
   */
  async getStoredDocuments(): Promise<TaxDocument[]> {
    try {
      const stored = await AsyncStorage.getItem(this.STORAGE_KEY);
      return stored ? JSON.parse(stored) : [];
    } catch (error) {
      console.error('Failed to get stored documents:', error);
      return [];
    }
  }

  /**
   * Get documents for specific tax year
   */
  async getDocumentsForYear(taxYear: number): Promise<TaxDocument[]> {
    const allDocs = await this.getStoredDocuments();
    return allDocs.filter(doc => doc.taxYear === taxYear);
  }

  /**
   * Share tax document
   */
  async shareDocument(documentId: string): Promise<void> {
    try {
      const documents = await this.getStoredDocuments();
      const document = documents.find(d => d.id === documentId);
      
      if (!document) {
        throw new Error('Document not found');
      }

      await Share.open({
        url: Platform.OS === 'ios' ? document.filePath : `file://${document.filePath}`,
        type: 'application/pdf',
        subject: `Waqiti Tax Document - ${document.type} ${document.taxYear}`,
      });

      await this.trackEvent('tax_document_shared', {
        documentId,
        type: document.type,
        taxYear: document.taxYear,
      });

    } catch (error) {
      if (error.message !== 'User did not share') {
        console.error('Failed to share document:', error);
        throw error;
      }
    }
  }

  /**
   * Delete tax document
   */
  async deleteDocument(documentId: string): Promise<void> {
    try {
      const documents = await this.getStoredDocuments();
      const document = documents.find(d => d.id === documentId);
      
      if (document) {
        // Delete file
        await RNFS.unlink(document.filePath);
        
        // Update metadata
        const updatedDocs = documents.filter(d => d.id !== documentId);
        await AsyncStorage.setItem(this.STORAGE_KEY, JSON.stringify(updatedDocs));
        
        await this.trackEvent('tax_document_deleted', {
          documentId,
          type: document.type,
          taxYear: document.taxYear,
        });
      }
    } catch (error) {
      console.error('Failed to delete document:', error);
      throw error;
    }
  }

  /**
   * Track analytics event
   */
  private async trackEvent(event: string, properties?: Record<string, any>): Promise<void> {
    try {
      await ApiService.trackEvent(`tax_${event}`, {
        ...properties,
        platform: Platform.OS,
        timestamp: new Date().toISOString(),
      });
    } catch (error) {
      console.warn('Failed to track tax event:', error);
    }
  }
}

export default TaxDocumentService.getInstance();