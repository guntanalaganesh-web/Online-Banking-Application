import React, { useState } from 'react';
import { useForm } from 'react-hook-form';
import { motion } from 'framer-motion';
import toast from 'react-hot-toast';
import { 
  ArrowsRightLeftIcon, GlobeAltIcon, BuildingLibraryIcon, 
  CheckCircleIcon, ExclamationTriangleIcon 
} from '@heroicons/react/24/outline';

const transferTypes = [
  { id: 'internal', name: 'Internal Transfer', icon: ArrowsRightLeftIcon, fee: 'Free' },
  { id: 'domestic', name: 'Domestic Wire', icon: BuildingLibraryIcon, fee: '$15.00' },
  { id: 'swift', name: 'SWIFT International', icon: GlobeAltIcon, fee: '$25.00' },
];

export default function Transfers() {
  const [transferType, setTransferType] = useState('internal');
  const [isProcessing, setIsProcessing] = useState(false);
  const { register, handleSubmit, formState: { errors }, reset } = useForm();

  const onSubmit = async (data) => {
    setIsProcessing(true);
    try {
      // Simulate API call
      await new Promise(resolve => setTimeout(resolve, 2000));
      toast.success('Transfer initiated successfully!');
      reset();
    } catch (error) {
      toast.error('Transfer failed. Please try again.');
    } finally {
      setIsProcessing(false);
    }
  };

  return (
    <div className="max-w-4xl mx-auto space-y-6">
      <h1 className="text-2xl font-bold text-white">Send Money</h1>

      {/* Transfer Type Selection */}
      <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
        {transferTypes.map((type) => (
          <motion.button
            key={type.id}
            whileHover={{ scale: 1.02 }}
            whileTap={{ scale: 0.98 }}
            onClick={() => setTransferType(type.id)}
            className={`p-4 rounded-xl border-2 transition-all ${
              transferType === type.id 
                ? 'border-blue-500 bg-blue-500/10' 
                : 'border-slate-700 bg-slate-800 hover:border-slate-600'
            }`}
          >
            <type.icon className={`w-8 h-8 mx-auto mb-2 ${
              transferType === type.id ? 'text-blue-400' : 'text-slate-400'
            }`} />
            <p className="text-white font-medium">{type.name}</p>
            <p className="text-sm text-slate-400">Fee: {type.fee}</p>
          </motion.button>
        ))}
      </div>

      {/* Transfer Form */}
      <motion.div
        initial={{ opacity: 0 }}
        animate={{ opacity: 1 }}
        className="bg-slate-800 border border-slate-700 rounded-xl p-6"
      >
        <form onSubmit={handleSubmit(onSubmit)} className="space-y-6">
          <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
            {/* From Account */}
            <div>
              <label className="block text-sm font-medium text-slate-300 mb-2">From Account</label>
              <select {...register('sourceAccount', { required: true })}
                className="w-full px-4 py-3 bg-slate-700 border border-slate-600 rounded-lg text-white">
                <option value="checking">Checking ****4523 - $45,230.00</option>
                <option value="savings">Savings ****8821 - $55,000.00</option>
              </select>
            </div>

            {/* Amount */}
            <div>
              <label className="block text-sm font-medium text-slate-300 mb-2">Amount</label>
              <div className="relative">
                <span className="absolute left-4 top-1/2 -translate-y-1/2 text-slate-400">$</span>
                <input type="number" step="0.01" {...register('amount', { required: true, min: 0.01 })}
                  className="w-full pl-8 pr-4 py-3 bg-slate-700 border border-slate-600 rounded-lg text-white"
                  placeholder="0.00" />
              </div>
              {errors.amount && <p className="text-red-400 text-sm mt-1">Valid amount required</p>}
            </div>
          </div>

          {/* Recipient Details */}
          <div className="border-t border-slate-700 pt-6">
            <h3 className="text-lg font-medium text-white mb-4">Recipient Details</h3>
            
            {transferType === 'internal' ? (
              <div>
                <label className="block text-sm font-medium text-slate-300 mb-2">Recipient Account</label>
                <input type="text" {...register('destinationAccount', { required: true })}
                  className="w-full px-4 py-3 bg-slate-700 border border-slate-600 rounded-lg text-white"
                  placeholder="Enter account number" />
              </div>
            ) : (
              <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                <div>
                  <label className="block text-sm font-medium text-slate-300 mb-2">Beneficiary Name</label>
                  <input type="text" {...register('beneficiaryName', { required: true })}
                    className="w-full px-4 py-3 bg-slate-700 border border-slate-600 rounded-lg text-white"
                    placeholder="Full name" />
                </div>
                
                {transferType === 'swift' && (
                  <>
                    <div>
                      <label className="block text-sm font-medium text-slate-300 mb-2">SWIFT/BIC Code</label>
                      <input type="text" {...register('swiftCode', { required: true, pattern: /^[A-Z]{6}[A-Z0-9]{2}([A-Z0-9]{3})?$/ })}
                        className="w-full px-4 py-3 bg-slate-700 border border-slate-600 rounded-lg text-white"
                        placeholder="e.g., CHASUS33" />
                    </div>
                    <div>
                      <label className="block text-sm font-medium text-slate-300 mb-2">IBAN</label>
                      <input type="text" {...register('iban', { required: true })}
                        className="w-full px-4 py-3 bg-slate-700 border border-slate-600 rounded-lg text-white"
                        placeholder="International Bank Account Number" />
                    </div>
                    <div>
                      <label className="block text-sm font-medium text-slate-300 mb-2">Bank Name</label>
                      <input type="text" {...register('bankName', { required: true })}
                        className="w-full px-4 py-3 bg-slate-700 border border-slate-600 rounded-lg text-white"
                        placeholder="Recipient's bank" />
                    </div>
                  </>
                )}
                
                <div className="md:col-span-2">
                  <label className="block text-sm font-medium text-slate-300 mb-2">Reference/Description</label>
                  <input type="text" {...register('description')}
                    className="w-full px-4 py-3 bg-slate-700 border border-slate-600 rounded-lg text-white"
                    placeholder="Payment reference" />
                </div>
              </div>
            )}
          </div>

          {/* Security Notice */}
          <div className="flex items-start gap-3 p-4 bg-yellow-500/10 border border-yellow-500/30 rounded-lg">
            <ExclamationTriangleIcon className="w-6 h-6 text-yellow-400 flex-shrink-0" />
            <div>
              <p className="text-yellow-400 font-medium">Security Notice</p>
              <p className="text-slate-400 text-sm">
                All transactions are monitored by our fraud detection system. 
                Large transfers may require additional verification.
              </p>
            </div>
          </div>

          {/* Submit Button */}
          <button
            type="submit"
            disabled={isProcessing}
            className="w-full py-4 bg-blue-600 hover:bg-blue-700 disabled:bg-slate-600 
                     text-white font-semibold rounded-lg transition-colors flex items-center justify-center gap-2"
          >
            {isProcessing ? (
              <>
                <svg className="animate-spin h-5 w-5" viewBox="0 0 24 24">
                  <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" fill="none" />
                  <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z" />
                </svg>
                Processing...
              </>
            ) : (
              <>
                <CheckCircleIcon className="w-5 h-5" />
                Confirm Transfer
              </>
            )}
          </button>
        </form>
      </motion.div>
    </div>
  );
}
