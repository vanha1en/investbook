/*
 * Portfolio
 * Copyright (C) 2020  Vitalii Ananev <an-vitek@ya.ru>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package ru.portfolio.portfolio.view;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ru.portfolio.portfolio.converter.SecurityEventCashFlowConverter;
import ru.portfolio.portfolio.converter.TransactionConverter;
import ru.portfolio.portfolio.pojo.*;
import ru.portfolio.portfolio.repository.SecurityEventCashFlowRepository;
import ru.portfolio.portfolio.repository.TransactionRepository;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import static ru.portfolio.portfolio.pojo.SecurityType.getCurrencyPair;

@Component
@RequiredArgsConstructor
public class PositionsFactory {

    private final TransactionRepository transactionRepository;
    private final SecurityEventCashFlowRepository securityEventCashFlowRepository;
    private final TransactionConverter transactionConverter;
    private final SecurityEventCashFlowConverter securityEventCashFlowConverter;
    private final Map<Portfolio, Map<String, Positions>> positionsCache = new ConcurrentHashMap<>();

    public Positions get(Portfolio portfolio, Security security) {
        return get(portfolio, security.getIsin());
    }

    public Positions get(Portfolio portfolio, String isinOrContract) {
        return positionsCache.computeIfAbsent(portfolio, k -> new ConcurrentHashMap<>())
                .computeIfAbsent(getCacheKey(isinOrContract), k -> create(portfolio, isinOrContract));
    }

    private String getCacheKey(String isinOrContract) {
        return (SecurityType.getSecurityType(isinOrContract) == SecurityType.CURRENCY_PAIR) ?
                getCurrencyPair(isinOrContract) :
                isinOrContract;
    }

    private Positions create(Portfolio portfolio, String isinOrContract) {
        SecurityType type = SecurityType.getSecurityType(isinOrContract);
        LinkedList<Transaction> transactions = new LinkedList<>();
        if (type == SecurityType.CURRENCY_PAIR) {
            String currencyPair = getCurrencyPair(isinOrContract);
            transactions.addAll(getTransactions(portfolio, currencyPair + "_TOM"));
            transactions.addAll(getTransactions(portfolio, currencyPair + "_TOD"));
            transactions.sort(
                    Comparator.comparing(Transaction::getTimestamp)
                            .thenComparingLong(Transaction::getId));
        } else {
            transactions.addAll(getTransactions(portfolio, isinOrContract));
        }
        Deque<SecurityEventCashFlow> redemption = (type == SecurityType.STOCK_OR_BOND) ?
                getRedemption(portfolio, isinOrContract) :
                new ArrayDeque<>(0);
        return new Positions(transactions, redemption);
    }

    private LinkedList<Transaction> getTransactions(Portfolio portfolio, String isin) {
        return transactionRepository
                .findBySecurityIsinAndPkPortfolioOrderByTimestampAscPkIdAsc(isin, portfolio.getId())
                .stream()
                .map(transactionConverter::fromEntity)
                .collect(Collectors.toCollection(LinkedList::new));
    }

    private Deque<SecurityEventCashFlow> getRedemption(Portfolio portfolio, String isin) {
        return securityEventCashFlowRepository
                .findByPortfolioIdAndSecurityIsinAndCashFlowTypeIdOrderByTimestampAsc(
                        portfolio.getId(),
                        isin,
                        CashFlowType.REDEMPTION.getId())
                .stream()
                .map(securityEventCashFlowConverter::fromEntity)
                .collect(Collectors.toCollection(LinkedList::new));
    }
}
